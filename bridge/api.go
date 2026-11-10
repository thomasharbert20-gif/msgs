package gmbridge

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"time"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/util"
)

const requestTimeout = 45 * time.Second

func reqCtx() (context.Context, context.CancelFunc) {
	return context.WithTimeout(context.Background(), requestTimeout)
}

func toJSON(v any) (string, error) {
	data, err := json.Marshal(v)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func friendlySendError(err error) error {
	if errors.Is(err, libgm.ErrPhoneNotResponding) {
		return errors.New("your phone didn't respond. Check that it's online")
	}
	return err
}

// ---------- conversations ----------

// ListConversations returns {"conversations":[...],"cursor":{...}} as JSON.
// folder: 1 inbox, 2 archive, 5 spam and blocked. cursorJSON may be empty.
func (b *Bridge) ListConversations(count int64, folder int64, cursorJSON string) (string, error) {
	cli, err := b.cli()
	if err != nil {
		return "", err
	}
	req := &gmproto.ListConversationsRequest{
		Count:  count,
		Folder: gmproto.ListConversationsRequest_Folder(folder),
	}
	if cursorJSON != "" {
		var cur gmproto.Cursor
		if err = json.Unmarshal([]byte(cursorJSON), &cur); err == nil && cur.LastItemID != "" {
			req.Cursor = &cur
		}
	}
	ctx, cancel := reqCtx()
	defer cancel()
	resp, err := cli.ListConversations(ctx, req)
	if err != nil {
		return "", friendlySendError(err)
	}
	convs := make([]map[string]any, 0, len(resp.GetConversations()))
	for _, conv := range resp.GetConversations() {
		b.storeConv(conv)
		convs = append(convs, b.convToMap(conv))
	}
	out := map[string]any{"conversations": convs}
	if c := resp.GetCursor(); c != nil {
		out["cursor"] = c
	}
	return toJSON(out)
}

// GetConversation fetches one conversation as JSON.
func (b *Bridge) GetConversation(convID string) (string, error) {
	cli, err := b.cli()
	if err != nil {
		return "", err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	conv, err := cli.GetConversation(ctx, convID)
	if err != nil {
		return "", friendlySendError(err)
	}
	b.storeConv(conv)
	return toJSON(b.convToMap(conv))
}

// FetchMessages returns {"messages":[...],"cursor":{...},"total":n} as JSON, newest first.
func (b *Bridge) FetchMessages(convID string, count int64, cursorJSON string) (string, error) {
	cli, err := b.cli()
	if err != nil {
		return "", err
	}
	var cursor *gmproto.Cursor
	if cursorJSON != "" {
		var cur gmproto.Cursor
		if err = json.Unmarshal([]byte(cursorJSON), &cur); err == nil && cur.LastItemID != "" {
			cursor = &cur
		}
	}
	ctx, cancel := reqCtx()
	defer cancel()
	if _, err = b.getConv(ctx, convID); err != nil {
		b.log.Warn().Err(err).Msg("Couldn't load conversation before fetching messages")
	}
	resp, err := cli.FetchMessages(ctx, convID, count, cursor)
	if err != nil {
		return "", friendlySendError(err)
	}
	msgs := make([]map[string]any, 0, len(resp.GetMessages()))
	for _, m := range resp.GetMessages() {
		msgs = append(msgs, b.msgToMap(m, true))
	}
	out := map[string]any{"messages": msgs, "total": resp.GetTotalMessages()}
	if c := resp.GetCursor(); c != nil {
		out["cursor"] = c
	}
	return toJSON(out)
}

// MarkRead marks the conversation read up to messageID.
func (b *Bridge) MarkRead(convID, messageID string) error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	return friendlySendError(cli.MarkRead(ctx, convID, messageID))
}

// SetTyping tells the other side you're typing.
func (b *Bridge) SetTyping(convID string) error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	conv, err := b.getConv(ctx, convID)
	if err != nil {
		return err
	}
	return cli.SetTyping(ctx, convID, b.simPayloadFor(conv))
}

// DeleteConversation deletes a conversation on the phone.
func (b *Bridge) DeleteConversation(convID string) error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	phone := ""
	if conv, err := b.getConv(ctx, convID); err == nil && !conv.GetIsGroupChat() {
		for _, p := range conv.GetParticipants() {
			if !p.GetIsMe() {
				phone = p.GetID().GetNumber()
				break
			}
		}
	}
	return friendlySendError(cli.DeleteConversation(ctx, convID, phone))
}

// SetArchived archives or unarchives a conversation.
func (b *Bridge) SetArchived(convID string, archived bool) error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	status := gmproto.ConversationStatus_ACTIVE
	if archived {
		status = gmproto.ConversationStatus_ARCHIVED
	}
	ctx, cancel := reqCtx()
	defer cancel()
	_, err = cli.UpdateConversation(ctx, &gmproto.UpdateConversationRequest{
		ConversationID: convID,
		Data: &gmproto.UpdateConversationRequest_UpdateData{UpdateData: &gmproto.UpdateConversationData{
			ConversationID: convID,
			Data:           &gmproto.UpdateConversationData_Status{Status: status},
		}},
	})
	return friendlySendError(err)
}

// SetMuted mutes or unmutes a conversation on the phone.
func (b *Bridge) SetMuted(convID string, muted bool) error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	mute := gmproto.ConversationMuteStatus_UNMUTE
	if muted {
		mute = gmproto.ConversationMuteStatus_MUTE
	}
	ctx, cancel := reqCtx()
	defer cancel()
	_, err = cli.UpdateConversation(ctx, &gmproto.UpdateConversationRequest{
		ConversationID: convID,
		Data: &gmproto.UpdateConversationRequest_UpdateData{UpdateData: &gmproto.UpdateConversationData{
			ConversationID: convID,
			Data:           &gmproto.UpdateConversationData_Mute{Mute: mute},
		}},
	})
	return friendlySendError(err)
}

// ---------- sending ----------

var sendRetryBackoff = []time.Duration{3 * time.Second, 8 * time.Second}

func (b *Bridge) send(ctx context.Context, cli *libgm.Client, req *gmproto.SendMessageRequest) error {
	resp, err := cli.SendMessage(ctx, req)
	for attempt := 0; err == nil && attempt < len(sendRetryBackoff) &&
		(resp.GetStatus() == gmproto.SendMessageResponse_FAILURE_2 || resp.GetStatus() == gmproto.SendMessageResponse_FAILURE_3); attempt++ {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(sendRetryBackoff[attempt]):
		}
		resp, err = cli.SendMessage(ctx, req)
	}
	if err != nil {
		return friendlySendError(err)
	}
	if resp.GetStatus() != gmproto.SendMessageResponse_SUCCESS {
		return fmt.Errorf("Google Messages on your phone couldn't send it (%s)", resp.GetStatus().String())
	}
	return nil
}

func (b *Bridge) baseSendRequest(conv *gmproto.Conversation, replyToID, tmpID string) (*gmproto.SendMessageRequest, string) {
	if tmpID == "" {
		tmpID = util.GenerateTmpID()
	}
	req := &gmproto.SendMessageRequest{
		ConversationID: conv.GetConversationID(),
		MessagePayload: &gmproto.MessagePayload{
			TmpID:          tmpID,
			ConversationID: conv.GetConversationID(),
			ParticipantID:  conv.GetDefaultOutgoingID(),
			TmpID2:         tmpID,
		},
		SIMPayload: b.simPayloadFor(conv),
		TmpID:      tmpID,
	}
	if replyToID != "" {
		req.Reply = &gmproto.ReplyPayload{MessageID: replyToID}
	}
	return req, tmpID
}

// SendText sends a text message and returns its temporary ID. The real message
// arrives later as a "message" event with the same tmpId. Pass tmpID to choose
// the temporary ID yourself (so a fast echo can be matched), or "" to generate one.
func (b *Bridge) SendText(convID, text, replyToID, tmpID string) (string, error) {
	cli, err := b.cli()
	if err != nil {
		return "", err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	conv, err := b.getConv(ctx, convID)
	if err != nil {
		return "", err
	}
	req, tmpID := b.baseSendRequest(conv, replyToID, tmpID)
	req.MessagePayload.MessageInfo = []*gmproto.MessageInfo{{
		Data: &gmproto.MessageInfo_MessageContent{MessageContent: &gmproto.MessageContent{Content: text}},
	}}
	if err = b.send(ctx, cli, req); err != nil {
		return "", err
	}
	return tmpID, nil
}

// SendMedia uploads a file and sends it. Returns the temporary ID.
func (b *Bridge) SendMedia(convID string, data []byte, fileName, mime, replyToID, tmpID string) (string, error) {
	cli, err := b.cli()
	if err != nil {
		return "", err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Minute)
	defer cancel()
	conv, err := b.getConv(ctx, convID)
	if err != nil {
		return "", err
	}
	media, err := cli.UploadMedia(ctx, data, fileName, mime)
	if err != nil {
		return "", fmt.Errorf("couldn't upload the file: %w", err)
	}
	req, tmpID := b.baseSendRequest(conv, replyToID, tmpID)
	req.MessagePayload.MessageInfo = []*gmproto.MessageInfo{{
		Data: &gmproto.MessageInfo_MediaContent{MediaContent: media},
	}}
	if err = b.send(ctx, cli, req); err != nil {
		return "", err
	}
	return tmpID, nil
}

// SendReaction adds ("add"), switches ("switch") or removes ("remove") a reaction.
func (b *Bridge) SendReaction(convID, messageID, emoji, action string) error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	act := gmproto.SendReactionRequest_ADD
	switch action {
	case "remove":
		act = gmproto.SendReactionRequest_REMOVE
	case "switch":
		act = gmproto.SendReactionRequest_SWITCH
	}
	req := &gmproto.SendReactionRequest{
		MessageID:    messageID,
		ReactionData: gmproto.MakeReactionData(emoji),
		Action:       act,
	}
	if conv, err := b.getConv(ctx, convID); err == nil {
		req.SIMPayload = b.simPayloadFor(conv)
	}
	resp, err := cli.SendReaction(ctx, req)
	if err != nil {
		return friendlySendError(err)
	}
	if !resp.GetSuccess() {
		return errors.New("Google Messages on your phone rejected the reaction")
	}
	return nil
}

// DeleteMessage deletes a message on the phone.
func (b *Bridge) DeleteMessage(messageID string) error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	resp, err := cli.DeleteMessage(ctx, messageID)
	if err != nil {
		return friendlySendError(err)
	}
	if !resp.GetSuccess() {
		return errors.New("Google Messages on your phone couldn't delete it")
	}
	return nil
}

// ---------- media and avatars ----------

// DownloadMedia downloads and decrypts an attachment. keyB64 is the "key" (or
// "thumbKey") from the message JSON.
func (b *Bridge) DownloadMedia(mediaID, keyB64 string) ([]byte, error) {
	cli, err := b.cli()
	if err != nil {
		return nil, err
	}
	key, err := base64.StdEncoding.DecodeString(keyB64)
	if err != nil {
		return nil, fmt.Errorf("bad media key: %w", err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Minute)
	defer cancel()
	rc, err := cli.DownloadMedia(ctx, mediaID, key)
	if err != nil {
		return nil, err
	}
	defer rc.Close()
	return io.ReadAll(rc)
}

// DownloadMediaToFile downloads and decrypts an attachment straight to a file.
func (b *Bridge) DownloadMediaToFile(mediaID, keyB64, path string) error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	key, err := base64.StdEncoding.DecodeString(keyB64)
	if err != nil {
		return fmt.Errorf("bad media key: %w", err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
	defer cancel()
	rc, err := cli.DownloadMedia(ctx, mediaID, key)
	if err != nil {
		return err
	}
	defer rc.Close()
	tmp := path + ".part"
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o600)
	if err != nil {
		return err
	}
	if _, err = io.Copy(f, rc); err != nil {
		_ = f.Close()
		_ = os.Remove(tmp)
		return err
	}
	if err = f.Close(); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

// GetAvatars returns {"participantId": "base64 image", ...} for the given IDs (JSON array).
func (b *Bridge) GetAvatars(participantIDsJSON string) (string, error) {
	cli, err := b.cli()
	if err != nil {
		return "", err
	}
	var ids []string
	if err = json.Unmarshal([]byte(participantIDsJSON), &ids); err != nil {
		return "", err
	}
	if len(ids) == 0 {
		return "{}", nil
	}
	ctx, cancel := reqCtx()
	defer cancel()
	resp, err := cli.GetParticipantThumbnail(ctx, ids...)
	if err != nil {
		return "", friendlySendError(err)
	}
	out := map[string]string{}
	for _, th := range resp.GetThumbnail() {
		if buf := th.GetData().GetImageBuffer(); len(buf) > 0 {
			out[th.GetIdentifier()] = base64.StdEncoding.EncodeToString(buf)
		}
	}
	return toJSON(out)
}

// DownloadGroupAvatar fetches a group chat icon from its URL.
func (b *Bridge) DownloadGroupAvatar(url string) ([]byte, error) {
	cli, err := b.cli()
	if err != nil {
		return nil, err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	return cli.DownloadAvatar(ctx, url)
}

// ---------- contacts and new chats ----------

// ListContacts returns the phone's contacts as a JSON array.
func (b *Bridge) ListContacts() (string, error) {
	cli, err := b.cli()
	if err != nil {
		return "", err
	}
	ctx, cancel := reqCtx()
	defer cancel()
	resp, err := cli.ListContacts(ctx)
	if err != nil {
		return "", friendlySendError(err)
	}
	out := make([]map[string]any, 0, len(resp.GetContacts()))
	for _, c := range resp.GetContacts() {
		number := c.GetNumber().GetFormattedNumber()
		if number == "" {
			number = c.GetNumber().GetNumber()
		}
		out = append(out, map[string]any{
			"name":          c.GetName(),
			"number":        number,
			"rawNumber":     c.GetNumber().GetNumber(),
			"participantId": c.GetParticipantID(),
			"contactId":     c.GetContactID(),
			"color":         c.GetAvatarHexColor(),
		})
	}
	return toJSON(out)
}

// StartConversation opens (or creates) a chat with the given phone numbers
// (JSON array). groupName is used when a new RCS group has to be created.
func (b *Bridge) StartConversation(numbersJSON, groupName string) (string, error) {
	cli, err := b.cli()
	if err != nil {
		return "", err
	}
	var numbers []string
	if err = json.Unmarshal([]byte(numbersJSON), &numbers); err != nil || len(numbers) == 0 {
		return "", errors.New("add at least one number")
	}
	req := &gmproto.GetOrCreateConversationRequest{}
	for _, n := range numbers {
		req.Numbers = append(req.Numbers, &gmproto.ContactNumber{MysteriousInt: 2, Number: n, Number2: n})
	}
	ctx, cancel := reqCtx()
	defer cancel()
	resp, err := cli.GetOrCreateConversation(ctx, req)
	if err == nil && resp.GetStatus() == gmproto.GetOrCreateConversationResponse_CREATE_RCS {
		name := groupName
		create := true
		req.RCSGroupName = &name
		req.CreateRCSGroup = &create
		resp, err = cli.GetOrCreateConversation(ctx, req)
	}
	if err != nil {
		return "", friendlySendError(err)
	}
	conv := resp.GetConversation()
	if conv.GetConversationID() == "" {
		return "", errors.New("Google Messages didn't create the conversation")
	}
	b.storeConv(conv)
	return toJSON(b.convToMap(conv))
}

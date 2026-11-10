package gmbridge

import (
	"encoding/base64"
	"strings"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

// Timestamps from Google Messages are microseconds; everything sent to the app is
// milliseconds.

func (b *Bridge) convToMap(c *gmproto.Conversation) map[string]any {
	parts := make([]map[string]any, 0, len(c.GetParticipants()))
	for _, p := range c.GetParticipants() {
		number := p.GetFormattedNumber()
		if number == "" {
			number = p.GetID().GetNumber()
		}
		parts = append(parts, map[string]any{
			"id":        p.GetID().GetParticipantID(),
			"name":      p.GetFullName(),
			"first":     p.GetFirstName(),
			"number":    number,
			"rawNumber": p.GetID().GetNumber(),
			"isMe":      p.GetIsMe(),
			"color":     p.GetAvatarHexColor(),
			"contactId": p.GetContactID(),
			"visible":   p.GetIsVisible(),
		})
	}
	lm := c.GetLatestMessage()
	return map[string]any{
		"id":              c.GetConversationID(),
		"name":            c.GetName(),
		"isGroup":         c.GetIsGroupChat(),
		"unread":          c.GetUnread(),
		"pinned":          c.GetPinned(),
		"lastTs":          c.GetLastMessageTimestamp() / 1000,
		"preview":         lm.GetDisplayContent(),
		"previewFromMe":   lm.GetFromMe() != 0,
		"previewSender":   lm.GetDisplayName(),
		"previewStatus":   int32(lm.GetLatestMessageStatus().GetStatus()),
		"rcs":             c.GetType() == gmproto.ConversationType_RCS,
		"status":          int32(c.GetStatus()),
		"readOnly":        c.GetReadOnly(),
		"color":           c.GetAvatarHexColor(),
		"groupAvatarUrl":  c.GetGroupAvatarURL(),
		"outgoingId":      c.GetDefaultOutgoingID(),
		"latestMessageId": c.GetLatestMessageID(),
		"participants":    parts,
	}
}

func mediaToMap(md *gmproto.MediaContent, actionID string) map[string]any {
	mime := md.GetMimeType()
	if mime == "" {
		mime = libgm.FormatToMediaType[md.GetFormat()].Format
	}
	return map[string]any{
		"mediaId":  md.GetMediaID(),
		"key":      base64.StdEncoding.EncodeToString(md.GetDecryptionKey()),
		"thumbId":  md.GetThumbnailMediaID(),
		"thumbKey": base64.StdEncoding.EncodeToString(md.GetThumbnailDecryptionKey()),
		"mime":     mime,
		"name":     md.GetMediaName(),
		"size":     md.GetSize(),
		"width":    md.GetDimensions().GetWidth(),
		"height":   md.GetDimensions().GetHeight(),
		"actionId": actionID,
	}
}

func isFailedStatus(s gmproto.MessageStatusType) bool {
	switch s {
	case gmproto.MessageStatusType_OUTGOING_FAILED_GENERIC,
		gmproto.MessageStatusType_OUTGOING_FAILED_EMERGENCY_NUMBER,
		gmproto.MessageStatusType_OUTGOING_CANCELED,
		gmproto.MessageStatusType_OUTGOING_FAILED_TOO_LARGE,
		gmproto.MessageStatusType_OUTGOING_FAILED_RECIPIENT_LOST_RCS,
		gmproto.MessageStatusType_OUTGOING_FAILED_NO_RETRY_NO_FALLBACK,
		gmproto.MessageStatusType_OUTGOING_FAILED_RECIPIENT_DID_NOT_DECRYPT,
		gmproto.MessageStatusType_OUTGOING_FAILED_RECIPIENT_LOST_ENCRYPTION,
		gmproto.MessageStatusType_OUTGOING_FAILED_RECIPIENT_DID_NOT_DECRYPT_NO_MORE_RETRY,
		gmproto.MessageStatusType_OUTGOING_FAILED_RECIPIENT_NEGATIVE_DELIVERY,
		gmproto.MessageStatusType_OUTGOING_FAILED_TO_ENCRYPT,
		gmproto.MessageStatusType_INCOMING_DOWNLOAD_FAILED,
		gmproto.MessageStatusType_INCOMING_FAILED_TO_DECRYPT:
		return true
	}
	return false
}

func (b *Bridge) myParticipantIDs(convID string) map[string]bool {
	ids := map[string]bool{}
	b.stateMu.Lock()
	conv := b.convs[convID]
	b.stateMu.Unlock()
	if conv == nil {
		return ids
	}
	if id := conv.GetDefaultOutgoingID(); id != "" {
		ids[id] = true
	}
	for _, p := range conv.GetParticipants() {
		if p.GetIsMe() {
			ids[p.GetID().GetParticipantID()] = true
		}
	}
	return ids
}

func (b *Bridge) msgToMap(m *gmproto.Message, isOld bool) map[string]any {
	status := m.GetMessageStatus().GetStatus()
	st := int32(status)
	fromMe := (st >= 1 && st < 100) || m.GetSenderParticipant().GetIsMe()

	var texts []string
	media := make([]map[string]any, 0)
	for _, info := range m.GetMessageInfo() {
		if mc := info.GetMessageContent(); mc != nil && mc.GetContent() != "" {
			texts = append(texts, mc.GetContent())
		}
		if md := info.GetMediaContent(); md != nil {
			media = append(media, mediaToMap(md, info.GetActionMessageID()))
		}
		if loc := info.GetLocationContent(); loc != nil {
			if t := loc.GetMessageContent().GetContent(); t != "" {
				texts = append(texts, t)
			}
		}
	}

	mine := b.myParticipantIDs(m.GetConversationID())
	reactions := make([]map[string]any, 0, len(m.GetReactions()))
	for _, r := range m.GetReactions() {
		emoji := r.GetData().GetUnicode()
		if emoji == "" {
			emoji = r.GetData().GetType().Unicode()
		}
		if emoji == "" {
			continue
		}
		isMine := false
		for _, id := range r.GetParticipantIDs() {
			if mine[id] {
				isMine = true
			}
		}
		reactions = append(reactions, map[string]any{
			"emoji":        emoji,
			"count":        len(r.GetParticipantIDs()),
			"mine":         isMine,
			"participants": r.GetParticipantIDs(),
		})
	}

	return map[string]any{
		"id":            m.GetMessageID(),
		"convId":        m.GetConversationID(),
		"participantId": m.GetParticipantID(),
		"senderName":    m.GetSenderParticipant().GetFullName(),
		"fromMe":        fromMe,
		"ts":            m.GetTimestamp() / 1000,
		"status":        st,
		"statusName":    status.String(),
		"text":          strings.Join(texts, "\n"),
		"subject":       m.GetSubject(),
		"media":         media,
		"reactions":     reactions,
		"replyTo":       m.GetReplyMessage().GetMessageID(),
		"tmpId":         m.GetTmpID(),
		"msgType":       m.GetType(),
		"isOld":         isOld,
		"tombstone":     st >= 200 && st < 300,
		"deleted":       status == gmproto.MessageStatusType_MESSAGE_DELETED,
		"failed":        isFailedStatus(status),
		"errorText":     m.GetMessageStatus().GetErrMsg(),
	}
}

func (b *Bridge) settingsToMap(s *gmproto.Settings) map[string]any {
	sims := make([]map[string]any, 0, len(s.GetSIMCards()))
	for _, sim := range s.GetSIMCards() {
		sims = append(sims, map[string]any{
			"participantId": sim.GetSIMParticipant().GetID(),
			"carrier":       sim.GetSIMData().GetCarrierName(),
			"number":        sim.GetSIMData().GetFormattedPhoneNumber(),
			"color":         sim.GetSIMData().GetColorHex(),
			"rcs":           sim.GetRCSChats().GetEnabled(),
		})
	}
	return map[string]any{
		"sims":         sims,
		"rcsEnabled":   s.GetRCSSettings().GetIsEnabled(),
		"bugleVersion": s.GetBugleVersion(),
	}
}

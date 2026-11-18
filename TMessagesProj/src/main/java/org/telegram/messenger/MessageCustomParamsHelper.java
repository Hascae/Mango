package org.telegram.messenger;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;

public class MessageCustomParamsHelper {

    private static boolean isCommentEncryptionPreparing(TLRPC.Message message) {
        return message.action instanceof TLRPC.TL_messageActionGramTransfer
                && ((TLRPC.TL_messageActionGramTransfer) message.action).comment_encrypted_preparing;
    }

    public static boolean isEmpty(TLRPC.Message message) {
        return (
            !isCommentEncryptionPreparing(message) &&
            message.voiceTranscription == null &&
            message.translatedVoiceTranscription == null &&
            !message.voiceTranscriptionOpen &&
            !message.summarizedOpen &&
            message.summaryText == null &&
            message.translatedSummaryLanguage == null &&
            message.translatedSummaryText == null &&
            !message.voiceTranscriptionFinal &&
            !message.voiceTranscriptionRated &&
            !message.voiceTranscriptionForce &&
            message.voiceTranscriptionId == 0 &&
            !message.premiumEffectWasPlayed &&
            message.originalLanguage == null &&
            message.translatedToLanguage == null &&
            message.translatedPoll == null &&
            message.translatedText == null &&
            message.translatedRichMessage == null &&
            message.errorAllowedPriceStars == 0 &&
            message.errorNewPriceStars == 0 &&
            message.originalText == null &&
            message.localEditedText == null &&
            message.localEditedDate == 0
        );
    }

    public static void copyParams(TLRPC.Message fromMessage, TLRPC.Message toMessage) {
        toMessage.voiceTranscription = fromMessage.voiceTranscription;
        toMessage.voiceTranscriptionOpen = fromMessage.voiceTranscriptionOpen;
        toMessage.voiceTranscriptionFinal = fromMessage.voiceTranscriptionFinal;
        toMessage.voiceTranscriptionForce = fromMessage.voiceTranscriptionForce;
        toMessage.voiceTranscriptionRated = fromMessage.voiceTranscriptionRated;
        toMessage.voiceTranscriptionId = fromMessage.voiceTranscriptionId;
        toMessage.premiumEffectWasPlayed = fromMessage.premiumEffectWasPlayed;
        toMessage.originalLanguage = fromMessage.originalLanguage;
        toMessage.translatedToLanguage = fromMessage.translatedToLanguage;
        toMessage.translatedPoll = fromMessage.translatedPoll;
        toMessage.translatedText = fromMessage.translatedText;
        toMessage.translatedRichMessage = fromMessage.translatedRichMessage;
        toMessage.errorAllowedPriceStars = fromMessage.errorAllowedPriceStars;
        toMessage.errorNewPriceStars = fromMessage.errorNewPriceStars;
        toMessage.translatedVoiceTranscription = fromMessage.translatedVoiceTranscription;
        toMessage.summarizedOpen = fromMessage.summarizedOpen;
        toMessage.summaryText = fromMessage.summaryText;
        toMessage.translatedSummaryText = fromMessage.translatedSummaryText;
        toMessage.translatedSummaryLanguage = fromMessage.translatedSummaryLanguage;
        toMessage.originalText = fromMessage.originalText;
        toMessage.localEditedText = fromMessage.localEditedText;
        toMessage.localEditedDate = fromMessage.localEditedDate;
    }


    public static void readLocalParams(TLRPC.Message message, NativeByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return;
        }
        int version = byteBuffer.readInt32(true);
        TLObject params;
        switch (version) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                params = new Params_v2(message, version);
                break;
            default:
                throw new RuntimeException("can't read params version = " + version);
        }
        params.readParams(byteBuffer, true);
    }

    public static NativeByteBuffer writeLocalParams(TLRPC.Message message) {
        if (isEmpty(message)) {
            return null;
        }
        TLObject params = new Params_v2(message, Params_v2.VERSION);
        try {
            NativeByteBuffer nativeByteBuffer = new NativeByteBuffer(params.getObjectSize());
            params.serializeToStream(nativeByteBuffer);
            return nativeByteBuffer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static class Params_v2 extends TLObject {

        private final static int VERSION = 5;
        final TLRPC.Message message;
        final int version;
        int flags = 0;

        private Params_v2(TLRPC.Message message, int version) {
            this.message = message;
            this.version = version;
            flags |= message.voiceTranscription != null ? 1 : 0;
            flags |= message.voiceTranscriptionForce ? 2 : 0;

            flags |= message.originalLanguage != null ? 4 : 0;
            flags |= message.translatedToLanguage != null ? 8 : 0;
            flags |= message.translatedText != null ? 16 : 0;

            flags |= message.translatedPoll != null ? 32 : 0;

            flags |= message.errorAllowedPriceStars != 0 ? 64 : 0;
            flags |= message.errorNewPriceStars != 0 ? 128 : 0;

            flags |= message.translatedVoiceTranscription != null ? 256 : 0;

            flags = setFlag(flags, FLAG_10, message.summaryText != null);
            flags = setFlag(flags, FLAG_11, message.translatedSummaryText != null);
            flags = setFlag(flags, FLAG_12, message.translatedSummaryLanguage != null);

            flags = setFlag(flags, FLAG_13, message.translatedRichMessage != null);
            flags = setFlag(flags, FLAG_14, isCommentEncryptionPreparing(message));
            // This fork's local fields sit high, clear of the flags official adds in order.
            flags = setFlag(flags, FLAG_28, message.originalText != null);
            flags = setFlag(flags, FLAG_29, message.localEditedText != null);
            flags = setFlag(flags, FLAG_30, message.localEditedDate != 0);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(VERSION);
            flags = message.voiceTranscriptionForce ? (flags | 2) : (flags &~ 2);
            flags = message.summarizedOpen ? (flags | 512) : (flags &~ 512);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(message.voiceTranscription);
            }
            stream.writeBool(message.voiceTranscriptionOpen);
            stream.writeBool(message.voiceTranscriptionFinal);
            stream.writeBool(message.voiceTranscriptionRated);
            stream.writeInt64(message.voiceTranscriptionId);

            stream.writeBool(message.premiumEffectWasPlayed);

            if ((flags & 4) != 0) {
                stream.writeString(message.originalLanguage);
            }
            if ((flags & 8) != 0) {
                stream.writeString(message.translatedToLanguage);
            }
            if ((flags & 16) != 0) {
                message.translatedText.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                message.translatedPoll.serializeToStream(stream);
            }

            if ((flags & 64) != 0) {
                stream.writeInt64(message.errorAllowedPriceStars);
            }
            if ((flags & 128) != 0) {
                stream.writeInt64(message.errorNewPriceStars);
            }
            if ((flags & 256) != 0) {
                message.translatedVoiceTranscription.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_10)) {
                message.summaryText.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                message.translatedSummaryText.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_12)) {
                stream.writeString(message.translatedSummaryLanguage);
            }
            if (hasFlag(flags, FLAG_13)) {
                message.translatedRichMessage.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_28)) {
                message.originalText.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeString(message.localEditedText);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(message.localEditedDate);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(true);
            if (message.action instanceof TLRPC.TL_messageActionGramTransfer) {
                final TLRPC.TL_messageActionGramTransfer action = (TLRPC.TL_messageActionGramTransfer) message.action;
                // An update may carry ciphertext while storage still has the old local params.
                action.comment_encrypted_preparing = !action.comment_encrypted && hasFlag(flags, FLAG_14);
            }
            if ((flags & 1) != 0) {
                message.voiceTranscription = stream.readString(exception);
            }
            message.voiceTranscriptionForce = (flags & 2) != 0;
            message.summarizedOpen = (flags & 512) != 0;
            message.voiceTranscriptionOpen = stream.readBool(exception);
            message.voiceTranscriptionFinal = stream.readBool(exception);
            message.voiceTranscriptionRated = stream.readBool(exception);
            message.voiceTranscriptionId = stream.readInt64(exception);

            message.premiumEffectWasPlayed = stream.readBool(exception);

            if ((flags & 4) != 0) {
                message.originalLanguage = stream.readString(exception);
            }
            if ((flags & 8) != 0) {
                message.translatedToLanguage = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                message.translatedText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                message.translatedPoll = TranslateController.PollText.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 64) != 0) {
                message.errorAllowedPriceStars = stream.readInt64(exception);
            }
            if ((flags & 128) != 0) {
                message.errorNewPriceStars = stream.readInt64(exception);
            }
            if ((flags & 256) != 0) {
                message.translatedVoiceTranscription = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                message.summaryText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                message.translatedSummaryText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_12)) {
                message.translatedSummaryLanguage = stream.readString(exception);
            }
            if (version >= 5) {
                if (hasFlag(flags, FLAG_13)) {
                    message.translatedRichMessage = TL_iv.RichMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
                }
                if (hasFlag(flags, FLAG_28)) {
                    message.originalText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
                }
                if (hasFlag(flags, FLAG_29)) {
                    message.localEditedText = stream.readString(exception);
                }
                if (hasFlag(flags, FLAG_30)) {
                    message.localEditedDate = stream.readInt32(exception);
                }
            } else {
                // Versions 2 to 4 were written by this fork on 12.x, where flags 13 to 15 held its own fields.
                if (version >= 2 && hasFlag(flags, FLAG_13)) {
                    message.originalText = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
                }
                if (version >= 3 && hasFlag(flags, FLAG_14)) {
                    message.localEditedText = stream.readString(exception);
                }
                if (version >= 4 && hasFlag(flags, FLAG_15)) {
                    message.localEditedDate = stream.readInt32(exception);
                }
            }
        }

    }
}

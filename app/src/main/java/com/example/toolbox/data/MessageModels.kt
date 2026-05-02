package com.example.toolbox.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MessageTag(
    val id: Int,
    val text: String,
    val color: String
)

@Serializable
data class MessageSender(
    @SerialName("chat_id") val chatId: String,
    @SerialName("chat_type") val chatType: Int,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String,
    val tag: List<MessageTag> = emptyList()
)

@Serializable
data class MessageContent(
    val text: String = ""
)

@Serializable
data class Message(
    @SerialName("msg_id") val msgId: String,
    val sender: MessageSender,
    val direction: String, // "left" or "right"
    @SerialName("content_type") val contentType: Int,
    val content: MessageContent,
    @SerialName("send_time") val sendTime: Long,
    @SerialName("msg_seq") val msgSeq: Long,
    @SerialName("quote_msg_id") val quoteMsgId: String? = null,
    @SerialName("edit_time") val editTime: Long? = null,
    @SerialName("msg_delete_time") val msgDeleteTime: Long? = null
)

@Serializable
data class ChatStatus(
    val number: Int,
    val code: Int,
    val msg: String
)

@Serializable
data class GetMessagesResponse(
    val status: ChatStatus,
    val messages: List<Message> = emptyList(),
    @SerialName("can_send") val canSend: Boolean = true,
    val pagination: MessagePagination? = null
)

@Serializable
data class MessagePagination(
    val page: Int,
    @SerialName("per_page") val perPage: Int,
    val total: Int,
    val pages: Int
)

@Serializable
data class SendMessageRequest(
    @SerialName("chat_type") val chatType: Int,
    @SerialName("chat_id") val chatId: Int,
    @SerialName("content_type") val contentType: Int = 1,
    val data: MessageContent,
    @SerialName("quote_msg_id") val quoteMsgId: String? = null
)

@Serializable
data class SendMessageResponse(
    val status: ChatStatus,
    val msg: Message? = null
)

// ---------- UI 状态 ----------
data class MessageDetailUiState(
    val messages: List<Message> = emptyList(),
    val chatType: Int = 1, // 1: private, 2: group
    val chatId: Int = 0,
    val canSend: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val pagination: MessagePagination? = null,
    val hasMore: Boolean = false,
    val inputText: String = "",
    val selectedImages: List<String> = emptyList(),
    val isMarkdown: Boolean = false,
    val editingMessage: Message? = null,
    val dailyMessagesLeft: Int = 0,
    val isChatExpired: Boolean = false
)

// 撤回确认弹窗状态
data class RecallDialogState(
    val isOpen: Boolean = false,
    val messageId: String? = null
)

// 编辑弹窗状态
data class EditDialogState(
    val isOpen: Boolean = false,
    val message: Message? = null,
    val newContent: String = "",
    val newImages: List<String> = emptyList()
)

@Serializable
data class GroupCreator(
    val id: Int,
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
data class GroupInfo(
    val id: Int,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val description: String = "",
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("members_count") val membersCount: Int = 0,
    val status: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("is_joined") val isJoined: Boolean = false,
    val creator: GroupCreator? = null
)

@Serializable
data class GroupInfoResponse(
    val success: Boolean,
    val group: GroupInfo? = null,
    val message: String? = null
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String,
    @SerialName("is_private") val isPrivate: Boolean,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
data class CreateGroupResponse(
    val success: Boolean,
    val group: GroupInfo? = null,
    val message: String? = null
)

data class GroupUiState(
    val showDropdownMenu: Boolean = false,
    val showJoinDialog: Boolean = false,
    val showCreateDialog: Boolean = false,

    val searchGroupId: String = "",
    val isSearching: Boolean = false,
    val foundGroup: GroupInfo? = null,

    val createGroupName: String = "",
    val createGroupDescription: String = "",
    val createGroupIsPrivate: Boolean = false,
    val createGroupAvatarUrl: String = "",
    val isCreating: Boolean = false,

    val error: String? = null
)
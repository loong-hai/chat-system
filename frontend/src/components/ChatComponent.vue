
<template>
    <el-splitter class="chat-component">
        <!-- 左侧会话列表 -->
        <el-splitter-panel class="conversation-panel" size="30%" min="20%" max="45%">
            <ConversationList @select="handleConversationSelect" />
        </el-splitter-panel>

        <!-- 右侧聊天窗口 -->
        <el-splitter-panel class="chat-window-panel" size="70%">
            <ChatWindow v-if="chatStore.currentConversation" :friend-id="currentFriendId" />
            <div v-else class="empty-chat">
                <el-empty description="选择一个会话开始聊天" :image-size="200">
                    <template #description>
                        <span style="color: #909399; font-size: 14px;">选择一个会话开始聊天</span>
                    </template>
                </el-empty>
            </div>
        </el-splitter-panel>
    </el-splitter>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import ConversationList from './ConversationList.vue'
import ChatWindow from './ChatWindow.vue'
import type { Conversation } from '@/types/message'
const chatStore = useChatStore()



const currentFriendId = computed(() => {
    if (!chatStore.currentConversation) return 0
    // 私聊取 targetId，群聊需要另外处理
    return chatStore.currentConversation.targetId
})

const handleConversationSelect = (conv: Conversation) => {
    console.log('选中会话:', conv.targetName)
    // ChatWindow 通过 props.friendId 监听变化自动加载
}
</script>

<style scoped>
.chat-component {
    width: 100%;
    height: 100%;
}

.conversation-panel {
    height: 100%;
    background: #ffffff;
}

.chat-window-panel {
    height: 100%;
    background: #f5f5f5;
    overflow: hidden;
}

.empty-chat {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #f0f7ff 0%, #e3f2fd 100%);
}
</style>
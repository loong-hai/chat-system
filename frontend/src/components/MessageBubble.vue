<template>
  <div class="message-bubble-wrapper" :class="{ 'is-self': isSelf }">
    <div class="message-container">
      <!-- 对方消息：头像在左 -->
      <template v-if="!isSelf">
        <el-avatar class="avatar-sm" :src="message.senderAvatar"/>
        <div class="content-wrapper">
          <div class="sender-name">{{ message.senderName }}</div>
          <div class="bubble left">
            <MessageContent :message="message" />
          </div>
        </div>
      </template>

      <!-- 自己消息：头像在右 -->
      <template v-else>
        <div class="content-wrapper">
          <div class="bubble right">
            <MessageContent :message="message" />
            <span v-if="message.status === 'FAILED'" class="status-icon error" @click="handleRetry">!</span>
          </div>
        </div>
        <el-avatar class="avatar-sm" :src="message.senderAvatar || currentUserAvatar"  />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import type { ChatMessage } from '@/types/message'
import MessageContent from './MessageContent.vue'
interface Props {
  message: ChatMessage
}

const handleRetry = () => {
  if (props.message.status === 'FAILED') {
    // 调用重发逻辑，例如 wsService.resendMessage(props.message.messageId)
  }
}

const props = defineProps<Props>()
const userStore = useUserStore()

const currentUserAvatar = computed(() => userStore.userInfo?.avatarUrl || '')

// 判断是否为自己发送的消息
const isSelf = computed(() => {
  return props.message.senderId === userStore.userInfo?.userId
})
</script>

<style scoped lang="scss">

.message-bubble-wrapper {
  width: 100%;
  // 确保 padding 被正确计算
  box-sizing: border-box;
}

.message-container {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  // 防止消息内容溢出影响高度计算
  min-height: 40px;
}
.status-icon {
  font-size: 12px;
  margin-left: 4px;

  &.sending {
    color: #999;
    animation: rotate 1s linear infinite;
  }

  &.error {
    color: #f56c6c;
    font-weight: bold;
    cursor: pointer;
  }

  &.read {
    color: #409eff;
  }
}

.message-bubble-wrapper {
  padding: 8px 16px;

  &.is-self {
    .message-container {
      justify-content: flex-end;
    }

    .bubble {
      background-color: #95ec69;
      border-radius: 4px 4px 4px 16px;
    }
  }
}

.message-container {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 60%;
}

.sender-name {
  color: #999;
  margin-bottom: 4px;
  margin-left: 4px;
}

.bubble {
  padding: 10px 14px;
  border-radius: 4px 4px 16px 4px;
  background-color: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  word-break: break-word;
  line-height: 1.5;
  position: relative;

  &.left {
    background-color: #fff;
  }

  .status-icon {
    font-size: 12px;
    margin-left: 4px;
    color: #999;

    &.error {
      color: #f56c6c;
      font-weight: bold;
      cursor: pointer;
    }
  }
}
</style>
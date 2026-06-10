<!-- components/UserListItem.vue -->
<template>
  <div class="user-list-item" :class="{ 'is-selected': isSelected }" @click="handleClick">
    <div class="user-avatar-wrapper">
      <el-avatar :src="user.avatarUrl || '/default-avatar.png'" class="avatar-sm user-avatar">
        {{ getAvatarText(user) }}
      </el-avatar>
      <div v-if="showStatus" class="status-indicator" :class="getStatusClass(user)" />
    </div>

    <div class="user-info">
      <div class="name-line">
        <span class="display-name">{{ user.displayName || user.nickname || user.username }}</span>
        <span v-if="user.remark && user.remark !== user.nickname" class="original-name">
          ({{ user.nickname }})
        </span>
      </div>
      <div class="signature-line">
        <span class="signature">{{ user.signature || '暂无签名' }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { FriendVO } from '@/types/friend'

const props = defineProps<{
  user: FriendVO
  isSelected?: boolean
  showStatus?: boolean
}>()

const emit = defineEmits<{
  (e: 'click', user: FriendVO): void
}>()

const handleClick = () => {
  emit('click', props.user)
}

const getAvatarText = (user: FriendVO) => {
  return user.displayName?.charAt(0) || user.nickname?.charAt(0) || user.username?.charAt(0) || '?'
}

const getStatusClass = (user: FriendVO) => {
  switch (user.onlineStatus) {
    case 1: return 'status-online'
    case 2: return 'status-busy'
    case 3: return 'status-invisible'
    default: return 'status-offline'
  }
}
</script>

<style scoped>
.user-list-item {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f8f8f8;
  transition: background-color 0.2s;
}

.user-list-item:hover {
  background: #f5f7fa;
}

.user-list-item.is-selected {
  background: #ecf5ff;
}

.user-avatar-wrapper {
  position: relative;
  margin-right: 12px;
  flex-shrink: 0;
}

.user-avatar {
  background: #f5f7fa;
}

.status-indicator {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid #ffffff;
}

.status-indicator.status-online {
  background: #52c41a;
}

.status-indicator.status-busy {
  background: #fa8c16;
}

.status-indicator.status-offline {
  background: #bfbfbf;
}

.user-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.name-line {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
  overflow: hidden;
}

.display-name {
  font-size: 14px;
  font-weight: 500;
  color: #333333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.original-name {
  font-size: 12px;
  color: #999999;
  margin-left: 6px;
  flex-shrink: 0;
}

.signature-line {
  font-size: 12px;
  color: #999999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
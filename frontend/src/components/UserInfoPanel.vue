<!-- components/UserInfoPanel.vue -->
<template>
  <div class="user-info-panel-root">
    <div v-if="!user" class="empty-state">
      <el-empty description="选择一个用户查看详情" :image-size="200" />
    </div>

    <div v-else class="user-info-content">
      <!-- 第一栏：基本信息 -->
      <div class="info-section avatar-section">
        <div class="left-section">
          <el-avatar shape="circle" class="avatar-xxl" :src="user.avatarUrl" />
        </div>
        <div class="right-section">
          <div class="row">
            <el-text class="text-heading nickname">{{ user.nickname }}</el-text>
          </div>
          <div class="row">
            <el-text class="user-id">ID: {{ user.friendId }}</el-text>
          </div>
          <div class="row">
            <el-tag :type="getStatusType(user.onlineStatus)" size="small" effect="dark">
              {{ user.onlineStatusText || '离线' }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- 第二栏：签名 -->
      <div class="info-section">
        <div class="info-row">
          <el-icon-document class="icon" />
          <el-text class="signature-text" :line-clamp="3">
            {{ user.signature || '这个人很懒，暂无签名' }}
          </el-text>
        </div>
      </div>

      <!-- 第三栏：操作按钮 -->
      <div class="info-section actions-section">
        <el-button v-if="!isFriend" type="primary" size="large" class="add-friend-btn" @click="handleAddFriend">
          <el-icon>
            <Plus />
          </el-icon>
          添加为好友
        </el-button>

        <el-button v-else type="success" size="large" disabled class="add-friend-btn">
          <el-icon>
            <Check />
          </el-icon>
          已是好友
        </el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { Document as ElIconDocument, Plus, Check } from '@element-plus/icons-vue'
import { useFriendStore } from '@/stores/friend'
import type { FriendVO } from '@/types/friend'

const props = defineProps<{
  user: FriendVO | null
}>()

const emit = defineEmits<{
  (e: 'add-friend', user: FriendVO): void
}>()

const friendStore = useFriendStore()

// 判断是否已经是好友（虽然搜索出来理论上不是好友，但以防万一）
const isFriend = computed(() => {
  if (!props.user) return false
  return friendStore.isFriend(props.user.friendId)
})

const getStatusType = (status: number | undefined): 'success' | 'danger' | 'warning' | 'info' => {
  switch (status) {
    case 1: return 'success'
    case 2: return 'danger'
    case 3: return 'warning'
    default: return 'info'
  }
}

const handleAddFriend = () => {
  if (props.user) {
    emit('add-friend', props.user)
  }
}
</script>

<style scoped>
.user-info-panel-root {
  height: 100%;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #f0f7ff 0%, #e3f2fd 100%);
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-info-content {
  height: 80%;
  width: 65%;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.info-section {
  position: relative;
  padding-bottom: 24px;
}

.info-section:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  height: 1px;
  background-color: #e0e0e0;
  transform: scaleY(0.5);
  transform-origin: 0 0;
}

.avatar-section {
  display: flex;
  gap: 20px;
}

.left-section {
  flex-shrink: 0;
}

.right-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.nickname {
  font-weight: 600;
  color: #171717;
}

.user-id {
  font-size: 14px;
  color: #666;
}

.info-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-top: 8px;
}

.icon {
  width: 24px;
  height: 24px;
  color: #606266;
  flex-shrink: 0;
  margin-top: 2px;
}

.signature-text {
  font-size: 16px;
  color: #171717;
  line-height: 1.6;
  flex: 1;
}

.actions-section {
  display: flex;
  justify-content: center;
  padding-top: 20px;
}

.add-friend-btn {
  width: 200px;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.add-friend-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
  transition: all 0.3s ease;
}
</style>
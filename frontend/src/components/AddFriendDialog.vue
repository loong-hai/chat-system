<!-- components/AddFriendDialog.vue -->
<!-- 好友申请面板 -->
<template>
  <el-dialog v-model="visible" title="添加好友" width="420px" :close-on-click-modal="false" class="add-friend-dialog"
    destroy-on-close>
    <div class="dialog-content" v-if="user">
      <div class="user-preview">
        <el-avatar class="avatar-lg" :src="user.avatarUrl" />
        <div class="user-preview-info">
          <div class="preview-name">{{ user.nickname }}</div>
          <div class="preview-id">ID: {{ user.friendId }}</div>
        </div>
      </div>

      <div class="form-section">
        <div class="form-label">验证消息</div>
        <el-input v-model="message" type="textarea" :rows="3" placeholder="请输入申请理由，例如：我是..." maxlength="100"
          show-word-limit resize="none" />
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading">
          发送申请
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useFriendStore } from '@/stores/friend'
import type { FriendVO } from '@/types/friend'

const props = defineProps<{
  modelValue: boolean
  user: FriendVO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const friendStore = useFriendStore()
const message = ref('')
const loading = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const handleSubmit = async () => {
  if (!props.user) return

  loading.value = true
  try {
    await friendStore.sendFriendRequest({
      receiverId: props.user.friendId,
      message: message.value.trim() || undefined
    })

    ElMessage.success('好友申请已发送')
    message.value = ''
    visible.value = false
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '发送失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dialog-content {
  padding: 10px 0;
}

.user-preview {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 20px;
}

.user-preview-info {
  flex: 1;
}

.preview-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.preview-id {
  font-size: 13px;
  color: #999;
}

.form-section {
  margin-top: 16px;
}

.form-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
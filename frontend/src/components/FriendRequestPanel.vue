<template>
  <div class="friend-request-panel">
    <!-- 列表区域 -->
    <el-scrollbar class="request-list">
      <div v-if="allRequests.length === 0" class="empty-state">
        <el-empty description="暂无好友申请" :image-size="120" />
      </div>
      
      <div v-else class="request-container">
        <div 
          v-for="request in allRequests" 
          :key="request.requestId" 
          class="request-card"
        >
          <!-- 头像区域 -->
          <el-avatar 
            :src="getAvatarUrl(request)" 
            class="avatar-lg request-avatar" 
          >
            {{ getAvatarText(request) }}
          </el-avatar>
          
          <!-- 内容区域 -->
          <div class="request-content">
            <div class="request-header">
              <span class="request-title">
                <template v-if="request.isSender">
                  申请添加 <strong>{{ request.receiverUsername }}</strong> 为好友
                </template>
                <template v-else>
                  <strong>{{ request.senderNickname }}</strong> 请求加为好友
                </template>
              </span>
              <span class="request-time">{{ formatTime(request.createdAt) }}</span>
            </div>
            
            <div class="request-reason" :title="request.message">
              {{ request.message || '暂无申请理由' }}
            </div>
          </div>

          <!-- 操作区域 -->
          <div class="request-actions">
            <!-- 自己发的 -->
            <template v-if="request.isSender">
              <el-tag type="primary" effect="light" size="small">已发出</el-tag>
            </template>
            
            <!-- 收到的未处理 -->
            <template v-else-if="request.status === 0">
              <el-button 
                type="primary" 
                size="small" 
                @click="handleAcceptClick(request)"
              >
                同意
              </el-button>
              <el-button 
                type="danger" 
                size="small" 
                plain 
                @click="handleRejectClick(request)"
              >
                拒绝
              </el-button>
            </template>
            
            <!-- 已处理 -->
            <template v-else>
              <el-tag 
                :type="request.status === 1 ? 'success' : 'info'" 
                effect="light" 
                size="small"
              >
                {{ request.statusText }}
              </el-tag>
            </template>
          </div>
        </div>
      </div>
    </el-scrollbar>

    <!-- 通过弹窗 -->
    <el-dialog
      v-model="acceptDialogVisible"
      title="添加好友"
      width="420px"
      :close-on-click-modal="false"
      destroy-on-close
      class="accept-dialog"
    >
      <div class="accept-form">
        <div class="full-reason">
          <div class="reason-label">申请理由：</div>
          <div class="reason-content">{{ currentRequest?.message || '暂无申请理由' }}</div>
        </div>
        
        <el-divider />
        
        <el-form :model="acceptForm" label-width="80px">
          <el-form-item label="备注">
            <el-input 
              v-model="acceptForm.remark" 
              :placeholder="currentRequest?.senderNickname || '设置好友备注'" 
              maxlength="50" 
              show-word-limit 
            />
          </el-form-item>
          
          <el-form-item label="分组">
            <el-select 
              v-model="acceptForm.groupId" 
              placeholder="选择分组（可选）" 
              style="width: 100%" 
              clearable
            >
              <el-option
                v-for="group in friendStore.groups"
                :key="group.groupId"
                :label="group.groupName"
                :value="group.groupId"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="acceptDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="processing" @click="confirmAccept">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 拒绝弹窗 -->
    <el-dialog
      v-model="rejectDialogVisible"
      title="拒绝好友申请"
      width="400px"
      :close-on-click-modal="false"
      destroy-on-close
      class="reject-dialog"
    >
      <div class="reject-form">
        <div class="reject-tips">拒绝后，对方将不会成为您的好友</div>
        <el-input
          v-model="rejectReason"
          type="textarea"
          :rows="3"
          placeholder="请输入拒绝理由（可选）"
          maxlength="100"
          show-word-limit
          resize="none"
        />
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="rejectDialogVisible = false">取消</el-button>
          <el-button type="danger" :loading="processing" @click="confirmReject">
            确定拒绝
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue'
import { useFriendStore } from '@/stores/friend'
import { userApi } from '@/api'
import type { FriendRequestVO,  } from '@/types/friend'
import { ElMessage } from 'element-plus'
import type { UserVO } from '@/types/user'
import { friendApi } from '@/api'  // 确保导入 friendApi
const friendStore = useFriendStore()
const acceptDialogVisible = ref(false)
const rejectDialogVisible = ref(false)
const processing = ref(false)
const currentRequest = ref<FriendRequestVO | null>(null)
const rejectReason = ref('')
const userInfoMap = ref<Map<number, UserVO>>(new Map())

const acceptForm = ref({
  remark: '',
  groupId: undefined as number | undefined
})

// 合并所有申请（收到+发出），按时间倒序
const allRequests = computed(() => {
  return [...friendStore.requests].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  )
})

// 获取头像URL（发出的申请需要查用户信息）
const getAvatarUrl = (request: FriendRequestVO): string | undefined => {
  if (request.isSender) {
    return userInfoMap.value.get(request.receiverId)?.avatarUrl
  }
  return request.senderAvatarUrl
}

// 获取头像文字（备用）
const getAvatarText = (request: FriendRequestVO): string => {
  if (request.isSender) {
    return request.receiverUsername?.charAt(0) || '?'
  }
  return request.senderNickname?.charAt(0) || '?'
}

// 格式化时间
const formatTime = (timeStr: string) => {
  const date = new Date(timeStr)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  
  if (isToday) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return '昨天'
  }
  
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

// 加载数据
const loadData = async () => {
  try {
    // 关键修复：同时获取收到的(type=received)和发出的(type=sent)
    const [received, sent] = await Promise.all([
      friendApi.getFriendRequests({ type: 'received' }),
      friendApi.getFriendRequests({ type: 'sent' })
    ])
    
    // 合并到 store（这里会包含 isSender=true/false 的标识）
    friendStore.requests = [...received, ...sent]
    
    await friendStore.fetchFriendGroups()
    
    // 只为发出的申请查询接收者头像（收到的申请自带 senderAvatarUrl）
    const uniqueReceiverIds = [...new Set(sent.map(r => r.receiverId))]
    
    await Promise.all(
      uniqueReceiverIds.map(async (userId) => {
        try {
          const userInfo = await userApi.getUserById(userId)
          userInfoMap.value.set(userId, userInfo)
        } catch (error) {
          console.error(`获取用户信息 ${userId} 失败:`, error)
        }
      })
    )
  } catch (error: any) {
    ElMessage.error('获取好友申请失败：' + error.message)
  }
}

// 点击同意
const handleAcceptClick = (request: FriendRequestVO) => {
  currentRequest.value = request
  acceptForm.value.remark = request.senderNickname || ''
  acceptForm.value.groupId = undefined
  acceptDialogVisible.value = true
}

// 点击拒绝
const handleRejectClick = (request: FriendRequestVO) => {
  currentRequest.value = request
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

// 确认同意
const confirmAccept = async () => {
  if (!currentRequest.value) return
  
  processing.value = true
  try {
    await friendStore.handleFriendRequest(currentRequest.value.requestId, {
      requestId: currentRequest.value.requestId,
      accept: true
    })
    
    // 处理分组
    if (acceptForm.value.groupId && currentRequest.value) {
      await friendStore.fetchFriends()
      const friend = friendStore.friends.find(f => f.friendId === currentRequest.value!.senderId)
      if (friend) {
        await friendStore.moveFriendToGroup(friend.relationId, acceptForm.value.groupId)
      }
    }
    
    // 处理备注
    const finalRemark = acceptForm.value.remark.trim() || currentRequest.value.senderNickname
    if (finalRemark && currentRequest.value) {
      const friend = friendStore.friends.find(f => f.friendId === currentRequest.value!.senderId)
      if (friend) {
        await friendStore.updateFriend(friend.friendId, {
          remark: finalRemark
        })
      }
    }
    
    ElMessage.success('已通过好友申请')
    acceptDialogVisible.value = false
    await loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    processing.value = false
  }
}

// 确认拒绝
const confirmReject = async () => {
  if (!currentRequest.value) return
  
  processing.value = true
  try {
    await friendStore.handleFriendRequest(currentRequest.value.requestId, {
      requestId: currentRequest.value.requestId,
      accept: false,
      rejectReason: rejectReason.value || undefined
    })
    
    ElMessage.success('已拒绝好友申请')
    rejectDialogVisible.value = false
    await loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    processing.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.friend-request-panel {
  height: 100%;
  width: 100%;
  background-color: #ffffff;
  display: flex;
  flex-direction: column;
}

.request-list {
  height: 100%;
  padding: 16px;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.request-container {
  padding: 0 8px;
}

/* 卡片样式：淡白色底色、圆角、边距 */
.request-card {
  display: flex;
  align-items: center;
  padding: 16px;
  margin-bottom: 12px;
  background-color: #fafafa;  /* 淡白色底色 */
  border-radius: 8px;         /* 圆角矩形 */
  border: 1px solid #ebeef5;  /* 细边框 */
  transition: all 0.3s ease;
}

.request-card:hover {
  background-color: #f5f7fa;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  transform: translateY(-1px);
}

.request-avatar {
  flex-shrink: 0;
  margin-right: 16px;
  background-color: #409eff;
  color: #fff;
  font-size: 18px;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.request-content {
  flex: 1;
  min-width: 0;
  margin-right: 16px;
}

.request-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.request-title {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.request-title strong {
  color: #409eff;
  font-weight: 600;
}

.request-time {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
  margin-left: 8px;
  font-weight: 400;
}

.request-reason {
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.5;
}

.request-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  align-items: center;
}

/* 弹窗样式 */
.accept-form,
.reject-form {
  padding: 10px 0;
}

.full-reason {
  margin-bottom: 8px;
}

.reason-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
  font-weight: 500;
}

.reason-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  border-left: 3px solid #409eff;
}

.reject-tips {
  font-size: 13px;
  color: #f56c6c;
  margin-bottom: 12px;
  padding: 8px 12px;
  background-color: #fef0f0;
  border-radius: 4px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.el-divider) {
  margin: 16px 0;
}
</style>
<template>
    <div class="search-panel-root">
        <el-splitter class="search-panel">
            <el-splitter-panel class="search-sidebar" size="30%" min="20%" max="45%">
                <div class="sidebar-container">
                    <div class="search-header">
                        <div class="search-input-wrapper">
                            <el-icon class="search-icon">
                                <Search />
                            </el-icon>
                            <el-input v-model="searchKeyword" placeholder="搜索用户ID/昵称" class="search-input" clearable
                                @keyup.enter="handleSearch" @clear="handleClear">
                                <template #append>
                                    <el-button @click="handleSearch">
                                        <el-icon>
                                            <Search />
                                        </el-icon>
                                    </el-button>
                                </template>
                            </el-input>
                        </div>
                    </div>

                    <div class="search-results">
                        <el-scrollbar v-if="!loading">
                            <div v-if="searchResults.length > 0" class="results-list">
                                <UserListItem v-for="friend in searchResults" :key="friend.friendId" :user="friend"
                                    :is-selected="selectedUser?.friendId === friend.friendId" show-status
                                    @click="handleUserSelect(friend)" />
                            </div>

                            <div v-else-if="hasSearched" class="empty-results">
                                <el-empty description="未找到相关用户" :image-size="120" />
                            </div>

                            <div v-else class="initial-state">
                                <div class="tips">
                                    <el-icon class="avatar-lg" color="#dcdfe6">
                                        <Search />
                                    </el-icon>
                                    <p>输入关键词开始搜索</p>
                                </div>
                            </div>
                        </el-scrollbar>

                        <div v-else class="loading-state">
                            <el-icon class="loading-icon">
                                <Loading />
                            </el-icon>
                            <span>搜索中...</span>
                        </div>
                    </div>
                </div>
            </el-splitter-panel>

            <el-splitter-panel class="search-detail" size="70%">
                <UserInfoPanel :user="selectedUser" @add-friend="handleShowAddDialog" />
            </el-splitter-panel>
        </el-splitter>

        <AddFriendDialog v-model="dialogVisible" :user="selectedUser" @success="handleRequestSuccess" />
    </div>
</template>
<script lang="ts" setup>
import { ref } from 'vue'
import { Search, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api'
import type { UserVO } from '@/types/user'
import type { FriendVO } from '@/types/friend'
import UserListItem from './UserListItem.vue'
import UserInfoPanel from './UserInfoPanel.vue'
import AddFriendDialog from './AddFriendDialog.vue'

const searchKeyword = ref('')
const searchResults = ref<FriendVO[]>([])
const selectedUser = ref<FriendVO | null>(null)
const loading = ref(false)
const hasSearched = ref(false)
const dialogVisible = ref(false)

/**
 * UserVO → FriendVO 适配器
 * 显式标注返回类型为 FriendVO，避免 undefined 推断
 */
const adaptUserToFriend = (user: UserVO): FriendVO => {
    const friend: FriendVO = {
        relationId: 0,
        friendId: user.userId,
        username: user.username,
        nickname: user.nickname,
        displayName: user.nickname,
        avatarUrl: user.avatarUrl,
        signature: user.signature,
        onlineStatus: user.onlineStatus,
        onlineStatusText: user.onlineStatusText,
        isOnline: user.isOnline,
        isPinned: false,
        isMuted: false,
        intimacyLevel: 0
    }
    return friend
}

const handleSearch = async () => {
    const keyword = searchKeyword.value.trim()
    if (!keyword) {
        ElMessage.warning('请输入搜索关键词')
        return
    }

    loading.value = true
    hasSearched.value = true

    try {
        const users: UserVO[] = await userApi.searchUsers(keyword)

        // 关键修复：使用类型断言确保 map 返回的是 FriendVO[]
        searchResults.value = users.map((user): FriendVO => adaptUserToFriend(user))

        if (searchResults.value.length === 1) {
            selectedUser.value = searchResults.value[0]!
        } else {
            selectedUser.value = null
        }
    } catch (error) {
        console.error('搜索失败:', error)
        ElMessage.error('搜索失败，请重试')
        searchResults.value = []
        selectedUser.value = null
    } finally {
        loading.value = false
    }
}

const handleClear = () => {
    searchResults.value = []
    hasSearched.value = false
    selectedUser.value = null
}

const handleUserSelect = (friend: FriendVO) => {
    selectedUser.value = friend
}

const handleShowAddDialog = (_friend: FriendVO) => {
    dialogVisible.value = true
}

const handleRequestSuccess = () => {
    console.log('好友申请发送成功')
}
</script>

<style scoped>
/* 样式保持不变 */
.search-panel-root {
    width: 100%;
    height: 100%;
    display: flex;
    position: relative;
}

.search-panel {
    width: 100%;
    height: 100%;
    background: #ffffff;
}

.search-sidebar {
    height: 100%;
    border-right: 1px solid #e4e7ed;
}

.sidebar-container {
    height: 100%;
    display: flex;
    flex-direction: column;
}

.search-header {
    padding: 16px;
}

.search-input-wrapper {
    position: relative;
    display: flex;
    align-items: center;
}

.search-icon {
    position: absolute;
    left: 12px;
    color: #909399;
    z-index: 1;
}

.search-input :deep(.el-input__wrapper) {
    padding-left: 32px;
}

.search-results {
    flex: 1;
    overflow: hidden;
    background: #ffffff;
}

.results-list {
    padding: 4px 0;
}

.empty-results,
.initial-state,
.loading-state {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
}

.initial-state {
    padding-top: 20px;
}

.tips {
    text-align: center;
    color: #909399;
}

.tips p {
    margin-top: 16px;
    font-size: 14px;
}

.loading-icon {
    animation: rotate 2s linear infinite;
    margin-right: 8px;
}

@keyframes rotate {
    from {
        transform: rotate(0deg);
    }

    to {
        transform: rotate(360deg);
    }
}

.search-detail {
    height: 100%;
    background: #f5f5f5;
    overflow: hidden;
}
</style>
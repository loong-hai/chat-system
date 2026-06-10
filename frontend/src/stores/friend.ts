// stores/friend.ts - 修复版本
import { defineStore } from 'pinia'
import { friendApi } from '@/api'
import type {
    FriendVO,
    FriendGroupVO,
    FriendRequestVO,
    FriendRequestDTO,
    FriendRequestHandleDTO,
    FriendUpdateDTO,
    FriendGroupDTO
} from '@/types/friend'

export const useFriendStore = defineStore('friend', {
    state: () => ({
        friends: [] as FriendVO[],
        groups: [] as FriendGroupVO[],
        requests: [] as FriendRequestVO[],
        loading: {
            friends: false,
            groups: false,
            requests: false
        },
        error: null as string | null
    }),

    getters: {
        groupedFriends(): Map<number | 'ungrouped', FriendVO[]> {
            const grouped = new Map<number | 'ungrouped', FriendVO[]>()

            // 关键修正：始终初始化所有分组，不管有没有好友
            this.groups.forEach(group => {
                grouped.set(group.groupId, [])
            })

            // 添加"未分组"
            grouped.set('ungrouped', [])

            // 分配好友到分组
            this.friends.forEach(friend => {
                if (friend.groupId && grouped.has(friend.groupId)) {
                    grouped.get(friend.groupId)!.push(friend)
                } else {
                    grouped.get('ungrouped')!.push(friend)
                }
            })

            return grouped
        }, displayGroups(): Array<{
            groupId: number | 'ungrouped'
            groupName: string
            count: number
            friends: FriendVO[]
        }> {
            const result: Array<{
                groupId: number | 'ungrouped'
                groupName: string
                count: number
                friends: FriendVO[]
            }> = []

            // 1. 先添加所有后端返回的分组
            this.groups.forEach(group => {
                const friendsInGroup = this.friends.filter(f => f.groupId === group.groupId)
                result.push({
                    groupId: group.groupId,
                    groupName: group.groupName,
                    count: friendsInGroup.length,
                    friends: friendsInGroup
                })
            })

            // 2. 添加未分组的好友
            const ungroupedFriends = this.friends.filter(f => !f.groupId)
            if (ungroupedFriends.length > 0) {
                result.push({
                    groupId: 'ungrouped',
                    groupName: '未分组',
                    count: ungroupedFriends.length,
                    friends: ungroupedFriends
                })
            }

            // 按默认分组排序：所有好友(0) -> 亲密好友(1) -> 家人(2) -> 同事(3) -> 同学(4) -> 其他 -> 未分组
            result.sort((a, b) => {
                // 未分组放在最后
                if (a.groupId === 'ungrouped') return 1
                if (b.groupId === 'ungrouped') return -1

                // 数字类型的分组按 sortOrder 排序
                if (typeof a.groupId === 'number' && typeof b.groupId === 'number') {
                    const groupA = this.groups.find(g => g.groupId === a.groupId)
                    const groupB = this.groups.find(g => g.groupId === b.groupId)
                    return (groupA?.sortOrder || 0) - (groupB?.sortOrder || 0)
                }

                return 0
            })

            return result
        },

        getGroupName(): (groupId: number | 'ungrouped') => string {
            return (groupId) => {
                if (groupId === 'ungrouped') return '未分组'
                const group = this.groups.find(g => g.groupId === groupId)
                return group ? group.groupName : `分组${groupId}`
            }
        },

        onlineCount(): number {
            // ✅ 使用 isVisibleOnline 计算可见的在线好友数（排除隐身用户）
            return this.friends.filter(friend =>
                friend.isVisibleOnline !== undefined ? friend.isVisibleOnline : friend.isOnline
            ).length
        },

        totalCount(): number {
            return this.friends.length
        },

        pendingRequestsCount(): number {
            return this.requests.filter(request => request.status === 0).length
        }
    },

    actions: {
        // ==================== 好友列表相关 ====================

        async fetchFriends() {
            this.loading.friends = true
            this.error = null

            try {
                this.friends = await friendApi.getFriendList()
                return this.friends
            } catch (error: any) {
                this.error = error.message || '获取好友列表失败'
                throw error
            } finally {
                this.loading.friends = false
            }
        },

        async fetchFriendDetail(friendId: number) {
            try {
                const friend = await friendApi.getFriendDetail(friendId)
                // 更新本地好友列表中的该好友信息
                const index = this.friends.findIndex(f => f.friendId === friendId)
                if (index !== -1) {
                    this.friends[index] = friend
                }
                return friend
            } catch (error: any) {
                this.error = error.message || '获取好友详情失败'
                throw error
            }
        },

        async updateFriend(friendId: number, data: FriendUpdateDTO) {
            try {
                await friendApi.updateFriend(friendId, data)

                // 更新本地数据
                const index = this.friends.findIndex(f => f.friendId === friendId)
                if (index !== -1) {
                    const friend = this.friends[index]! // 使用非空断言，因为我们已经检查了 index !== -1

                    if (data.remark !== undefined) {
                        friend.remark = data.remark
                        friend.displayName = data.remark || friend.nickname
                    }
                    if (data.groupId !== undefined) {
                        friend.groupId = data.groupId
                        // 更新分组名称
                        const group = this.groups.find(g => g.groupId === data.groupId)
                        if (group) {
                            friend.groupName = group.groupName
                        }
                    }
                    if (data.isPinned !== undefined) friend.isPinned = data.isPinned
                    if (data.isMuted !== undefined) friend.isMuted = data.isMuted
                    if (data.intimacyLevel !== undefined) friend.intimacyLevel = data.intimacyLevel
                }
            } catch (error: any) {
                this.error = error.message || '更新好友信息失败'
                throw error
            }
        },

        async deleteFriend(friendId: number) {
            try {
                await friendApi.deleteFriend(friendId)
                // 从本地列表中移除
                this.friends = this.friends.filter(f => f.friendId !== friendId)
            } catch (error: any) {
                this.error = error.message || '删除好友失败'
                throw error
            }
        },

        async blockFriend(friendId: number) {
            try {
                await friendApi.blockFriend(friendId)
                await this.fetchFriends() // 重新获取好友列表确保状态正确
            } catch (error: any) {
                this.error = error.message || '拉黑好友失败'
                throw error
            }
        },

        async unblockFriend(friendId: number) {
            try {
                await friendApi.unblockFriend(friendId)
                await this.fetchFriends()
            } catch (error: any) {
                this.error = error.message || '取消拉黑失败'
                throw error
            }
        },

        async searchFriends(keyword: string) {
            try {
                if (keyword.trim()) {
                    return await friendApi.searchFriends(keyword)
                } else {
                    return this.friends
                }
            } catch (error: any) {
                this.error = error.message || '搜索好友失败'
                throw error
            }
        },

        // ==================== 好友分组相关 ====================

        async fetchFriendGroups() {
            this.loading.groups = true
            this.error = null

            try {
                this.groups = await friendApi.getFriendGroups()
                return this.groups
            } catch (error: any) {
                this.error = error.message || '获取好友分组失败'
                throw error
            } finally {
                this.loading.groups = false
            }
        },

        async createFriendGroup(data: FriendGroupDTO) {
            try {
                const group = await friendApi.createFriendGroup(data)
                this.groups.push(group)
                return group
            } catch (error: any) {
                this.error = error.message || '创建分组失败'
                throw error
            }
        },

        async updateFriendGroup(groupId: number, data: FriendGroupDTO) {
            try {
                await friendApi.updateFriendGroup(groupId, data)

                // 更新本地数据
                const index = this.groups.findIndex(g => g.groupId === groupId)
                if (index !== -1) {
                    const group = this.groups[index]! // 非空断言
                    if (data.groupName) {
                        group.groupName = data.groupName
                    }
                    if (data.sortOrder !== undefined) {
                        group.sortOrder = data.sortOrder
                    }
                }
            } catch (error: any) {
                this.error = error.message || '更新分组失败'
                throw error
            }
        },

        async deleteFriendGroup(groupId: number) {
            try {
                await friendApi.deleteFriendGroup(groupId)
                // 从本地列表中移除
                this.groups = this.groups.filter(g => g.groupId !== groupId)

                // 将该分组的好友设置为未分组
                this.friends.forEach(friend => {
                    if (friend.groupId === groupId) {
                        friend.groupId = undefined
                        friend.groupName = undefined
                    }
                })
            } catch (error: any) {
                this.error = error.message || '删除分组失败'
                throw error
            }
        },

        async moveFriendToGroup(relationId: number, groupId: number) {
            try {
                await friendApi.moveFriendToGroup(relationId, groupId)

                // 更新本地数据
                const friend = this.friends.find(f => f.relationId === relationId)
                if (friend) {
                    friend.groupId = groupId
                    const group = this.groups.find(g => g.groupId === groupId)
                    if (group) {
                        friend.groupName = group.groupName
                    }
                }
            } catch (error: any) {
                this.error = error.message || '移动好友失败'
                throw error
            }
        },

        // ==================== 好友申请相关 ====================

        async fetchFriendRequests(params?: { status?: number; type?: string }) {
            this.loading.requests = true
            this.error = null

            try {
                this.requests = await friendApi.getFriendRequests(params)
                return this.requests
            } catch (error: any) {
                this.error = error.message || '获取好友申请失败'
                throw error
            } finally {
                this.loading.requests = false
            }
        },

        async sendFriendRequest(data: FriendRequestDTO) {
            try {
                await friendApi.sendFriendRequest(data)
            } catch (error: any) {
                this.error = error.message || '发送好友申请失败'
                throw error
            }
        },

        async handleFriendRequest(requestId: number, data: FriendRequestHandleDTO) {
            try {
                await friendApi.handleFriendRequest(requestId, data)

                // 更新本地申请状态
                const index = this.requests.findIndex(r => r.requestId === requestId)
                if (index !== -1) {
                    const request = this.requests[index]! // 非空断言
                    request.status = data.accept ? 1 : 2
                    request.statusText = data.accept ? '已同意' : '已拒绝'
                    request.processedAt = new Date().toISOString()

                    if (data.rejectReason) {
                        request.rejectReason = data.rejectReason
                    }
                }

                // 如果同意，重新获取好友列表
                if (data.accept) {
                    await this.fetchFriends()
                }
            } catch (error: any) {
                this.error = error.message || '处理好友申请失败'
                throw error
            }
        },

        // ==================== 辅助方法 ====================

        async initFriendData() {
            try {
                // 先获取分组，再获取好友
                await this.fetchFriendGroups()
                await this.fetchFriends()
            } catch (error) {
                console.error('初始化好友数据失败:', error)
                throw error
            }
        },

        clearFriendData() {
            this.friends = []
            this.groups = []
            this.requests = []
            this.error = null
            this.loading.friends = false
            this.loading.groups = false
            this.loading.requests = false
        },

        isFriend(userId: number): boolean {
            return this.friends.some(friend => friend.friendId === userId)
        },

        getFriendDisplayName(friend: FriendVO): string {
            return friend.remark || friend.nickname || friend.username
        },

        // 添加这个方法用于获取好友状态类名
        getFriendStatusClass(friend: FriendVO): string {
            // ✅ 优先使用 isVisibleOnline（考虑了隐身状态）
            // 如果后端返回了 isVisibleOnline，使用它；否则回退到 onlineStatus
            const isActuallyOnline = friend.isVisibleOnline !== undefined
                ? friend.isVisibleOnline
                : friend.isOnline

            if (!isActuallyOnline) {
                return 'status-offline'
            }

            // 在线时，根据业务状态显示不同样式
            switch (friend.bizStatus || friend.onlineStatusText) {
                case 'busy':
                case '忙碌':
                    return 'status-busy'
                case 'invisible':
                case '隐身':
                    // 虽然 isVisibleOnline=true 对自己可见，但显示为隐身样式
                    return 'status-invisible'
                case 'online':
                case '在线':
                default:
                    return 'status-online'
            }
        }
    }
})
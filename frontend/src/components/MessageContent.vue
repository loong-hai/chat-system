<template>
    <div class="message-content">
        <!-- 文本消息 -->
        <template v-if="message.type === 'TEXT'">
            {{ message.content }}
        </template>

        <!-- 图片消息 -->
        <template v-else-if="message.type === 'IMAGE'">
            <el-image :src="message.content" :preview-src-list="[message.content]" fit="cover" class="image-message">
                <template #error>
                    <div class="image-error">图片加载失败</div>
                </template>
            </el-image>
        </template>

        <!-- 语音消息 -->
        <template v-else-if="message.type === 'VOICE'">
            <audio controls :src="message.content" class="audio-player" />
            <div v-if="message.duration" class="file-info">时长: {{ formatDuration(message.duration) }}</div>
        </template>

        <!-- 视频消息 -->
        <template v-else-if="message.type === 'VIDEO'">
            <video controls :src="message.content" class="video-player" />
        </template>

        <!-- 文件消息 -->
        <template v-else-if="message.type === 'FILE'">
            <div class="file-message">
                <el-icon>
                    <Document />
                </el-icon>
                <div class="file-details">
                    <div class="file-name">{{ message.fileName || '文件' }}</div>
                    <div class="file-size">{{ formatFileSize(message.fileSize) }}</div>
                </div>
                <el-link type="primary" :href="message.content" target="_blank" :underline="false">下载</el-link>
            </div>
        </template>

        <!-- 其他类型 -->
        <template v-else>
            {{ message.content }}
        </template>
    </div>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'
import type { ChatMessage } from '@/types/message'

defineProps<{ message: ChatMessage }>()

const formatDuration = (seconds: number) => {
    const m = Math.floor(seconds / 60)
    const s = Math.floor(seconds % 60)
    return `${m}:${s.toString().padStart(2, '0')}`
}

const formatFileSize = (bytes?: number) => {
    if (!bytes) return ''
    const units = ['B', 'KB', 'MB', 'GB']
    let size = bytes
    let unitIndex = 0
    while (size >= 1024 && unitIndex < units.length - 1) {
        size /= 1024
        unitIndex++
    }
    return `${size.toFixed(1)} ${units[unitIndex]}`
}
</script>

<style scoped>
.image-message {
    max-width: 200px;
    max-height: 200px;
    border-radius: 4px;
    cursor: pointer;
}

.audio-player,
.video-player {
    max-width: 250px;
}

.file-message {
    display: flex;
    align-items: center;
    gap: 8px;
    background: #f5f7fa;
    padding: 8px 12px;
    border-radius: 4px;
}

.file-details {
    flex: 1;
}

.file-name {
    font-weight: 500;
    word-break: break-word;
}

.file-size {
    font-size: 12px;
    color: #909399;
}

.file-info {
    font-size: 12px;
    color: #999;
    margin-top: 4px;
}

.image-error {
    padding: 10px;
    background: #f5f5f5;
    color: #999;
    text-align: center;
}
</style>
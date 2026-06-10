<template>
  <div
    class="floating-card"
    :class="{ 
      'is-collapsed': collapsed,
      'is-resizing': resizing  // 新增：拉伸时禁用动画
    }"
    :style="cardStyle"
    @mousedown="startDrag"
  >
    <!-- 标题栏 -->
    <div class="card-header" @mousedown.stop="startDrag">
      <span class="card-title">{{ title }}</span>
      <div class="card-actions">
        <button v-if="collapsible" @click.stop="toggleCollapse" class="toggle-btn">
          {{ collapsed ? '▢' : '−' }}
        </button>
      </div>
    </div>
    
    <!-- 内容区 -->
    <div v-show="!collapsed" class="card-content">
      <div v-if="markdown" class="markdown-body" v-html="renderedMarkdown"></div>
      <div v-else-if="htmlContent" class="html-content" v-html="htmlContent"></div>
      <slot v-else></slot>
    </div>
    
    <!-- 调整大小手柄 -->
    <div v-show="!collapsed" class="resize-handle" @mousedown.stop="startResize"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue';
import { marked } from 'marked';

const props = defineProps<{
  title?: string;
  closable?: boolean;
  collapsible?: boolean;
  initialPosition?: { x: number; y: number };
  initialSize?: { width: number; height: number };
  initialCollapsed?: boolean;
  markdown?: string;
  htmlContent?: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'toggle', collapsed: boolean): void;
}>();

const collapsed = ref(props.initialCollapsed || false);
const expandedHeight = ref(props.initialSize?.height || 250);
const HEADER_HEIGHT = 40;
const resizing = ref(false); // 新增：标记是否正在拉伸

const position = ref(props.initialPosition || { x: 100, y: 100 });
const size = ref({ 
  width: props.initialSize?.width || 350,
  height: props.initialCollapsed ? HEADER_HEIGHT : (props.initialSize?.height || 250)
});

const cardStyle = computed(() => ({
  left: `${position.value.x}px`,
  top: `${position.value.y}px`,
  width: `${size.value.width}px`,
  height: collapsed.value ? `${HEADER_HEIGHT}px` : `${size.value.height}px`,
  overflow: 'hidden'
}));

const renderedMarkdown = computed(() => {
  if (!props.markdown) return '';
  try {
    return marked.parse(props.markdown, { breaks: true, gfm: true });
  } catch (e) {
    return props.markdown;
  }
});

const toggleCollapse = () => {
  if (collapsed.value) {
    size.value.height = Math.max(150, expandedHeight.value);
    collapsed.value = false;
  } else {
    expandedHeight.value = size.value.height;
    collapsed.value = true;
  }
  emit('toggle', collapsed.value);
};

// 拖拽逻辑（保持不变）
let dragging = false;
let startX = 0, startY = 0;
let startPos = { x: 0, y: 0 };
let startSize = { width: 0, height: 0 };

const startDrag = (e: MouseEvent) => {
  if ((e.target as HTMLElement).classList.contains('resize-handle')) return;
  if ((e.target as HTMLElement).closest('.card-actions')) return;
  
  dragging = true;
  startX = e.clientX;
  startY = e.clientY;
  startPos = { ...position.value };
  document.addEventListener('mousemove', onDrag);
  document.addEventListener('mouseup', stopDrag);
};

const onDrag = (e: MouseEvent) => {
  if (!dragging) return;
  position.value.x = startPos.x + (e.clientX - startX);
  position.value.y = startPos.y + (e.clientY - startY);
};

const stopDrag = () => {
  dragging = false;
  resizing.value = false; // 关键：结束拉伸时重置标记
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mousemove', onResize);
  document.removeEventListener('mouseup', stopDrag);
};

// 调整大小（关键修改）
const startResize = (e: MouseEvent) => {
  if (collapsed.value) return;
  e.stopPropagation();
  resizing.value = true; // 关键：开始拉伸时标记，禁用CSS动画
  startX = e.clientX;
  startY = e.clientY;
  startSize = { ...size.value };
  document.addEventListener('mousemove', onResize);
  document.addEventListener('mouseup', stopDrag);
};

const onResize = (e: MouseEvent) => {
  if (!resizing.value || collapsed.value) return;
  size.value.width = Math.max(200, startSize.width + (e.clientX - startX));
  size.value.height = Math.max(150, startSize.height + (e.clientY - startY));
  expandedHeight.value = size.value.height;
};

const handleClose = () => emit('close');

watch(() => props.markdown, (newVal, oldVal) => {
  if (newVal && newVal !== oldVal && collapsed.value && props.collapsible) {
    // 可选：新内容到达时自动展开
  }
});

onUnmounted(stopDrag);

// 暴露重置方法给父组件
const resetPosition = () => {
  position.value = { 
    x: props.initialPosition?.x || 100, 
    y: props.initialPosition?.y || 100 
  };
  // 同时重置大小
  size.value = {
    width: props.initialSize?.width || 350,
    height: collapsed.value ? HEADER_HEIGHT : (props.initialSize?.height || 250)
  };
};

defineExpose({
  resetPosition
});
</script>

<style scoped>
.floating-card {
  position: fixed;
  background: white;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  z-index: 9999;
  user-select: none;
  transition: height 0.25s cubic-bezier(0.4, 0, 0.2, 1); /* 折叠动画 */
  min-width: 200px;
}

/* 关键修复：拉伸时禁用所有transition，恢复即时响应 */
.floating-card.is-resizing {
  transition: none !important;
}

.is-collapsed {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.card-header {
  height: 40px;
  padding: 0 12px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
  border-bottom: 1px solid #e4e7ed;
  cursor: move;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #303133;
  flex-shrink: 0;
  border-radius: 8px 8px 0 0;
}

.is-collapsed .card-header {
  border-bottom: none;
  border-radius: 8px;
}

.card-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}

.card-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.card-actions button {
  background: transparent;
  border: none;
  font-size: 14px;
  line-height: 1;
  color: #909399;
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.card-actions button:hover {
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.toggle-btn {
  font-weight: bold;
  font-size: 12px;
}

.close-btn {
  font-size: 18px;
  line-height: 0.8;
}

.card-content {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  overflow-x: hidden;
  background: #fff;
}

.markdown-body {
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
}

.markdown-body :deep(h3) {
  color: #ff4d4f;
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 600;
  border-left: 3px solid #ff4d4f;
  padding-left: 8px;
}

.markdown-body :deep(h4) {
  color: #1890ff;
  margin: 10px 0 6px;
  font-size: 12px;
}

.markdown-body :deep(ul), 
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin: 6px 0;
  color: #333;
}

.markdown-body :deep(strong),
.markdown-body :deep(b) {
  color: #1890ff;
  font-weight: 600;
}

.markdown-body :deep(code) {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 11px;
  color: #d63384;
}

.markdown-body :deep(p) {
  margin: 8px 0;
}

.html-content {
  font-size: 12px;
  line-height: 1.6;
}


.resize-handle {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 16px;
  height: 16px;
  cursor: nwse-resize;
  background: transparent;  /* 清除原来的X形背景 */
  z-index: 10;
  opacity: 0.6;
  transition: opacity 0.2s;
}

/* 使用伪元素绘制直角拐角（└ 旋转后变成右下角）*/
.resize-handle::before {
  content: '';
  position: absolute;
  right: 4px;      /* 贴边距离 */
  bottom: 4px;
  width: 8px;      /* 线条长度 */
  height: 8px;
  border-right: 2px solid rgba(0, 0, 0, 0.3);   /* 垂直线 */
  border-bottom: 2px solid rgba(0, 0, 0, 0.3);  /* 水平线 */
  border-radius: 0 0 2px 0;  /* 稍微圆角，更柔和 */
  transition: border-color 0.2s;
}

/* Hover 效果：变蓝色 */
.resize-handle:hover {
  opacity: 1;
}

.resize-handle:hover::before {
  border-right-color: #409eff;
  border-bottom-color: #409eff;
}



.card-content::-webkit-scrollbar {
  width: 6px;
}

.card-content::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}

.card-content::-webkit-scrollbar-track {
  background: transparent;
}
</style>
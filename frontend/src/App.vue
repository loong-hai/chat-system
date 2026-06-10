<template>
  <router-view v-slot="{ Component, route }">
    <!-- 移除 mode="out-in"，改用默认的同步模式，添加 appear 确保首次渲染 -->
    <transition name="fade" appear>
      <component :is="Component" :key="route.fullPath" />
    </transition>
  </router-view>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 确保离开时不占空间，避免闪烁 */
.fade-leave-active {
  position: absolute;
  width: 100%;
}
</style>
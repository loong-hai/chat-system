<template>
    <el-splitter class="friendListAndChatArea">
        <el-splitter-panel class="friendList" size="30%" min="20%" max="45%">
            社交圈
        </el-splitter-panel>
        <el-splitter-panel class="chatArea" size="70%"></el-splitter-panel>
    </el-splitter>
</template>
<script lang="ts" setup>

</script>
<style scoped>
.friendList {
    height: 100%;
    width: 20%;
}

.chatArea {
    height: 100%;
    width: 80%;
}

.friendListAndChatArea {
    width: 100%;
    height: 100%;
}
</style>
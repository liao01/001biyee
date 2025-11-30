<template>
  <div class="info-box">
    <button class="close" @click="$emit('close')">×</button>
    <h4>{{ location.formattedAddress }}</h4>
    <p>{{ location.city }}, {{ location.district }}</p>
  </div>
</template>

<script setup>
defineProps({
  location: Object
});
</script>

<style scoped>
.info-box {
  background: white;
  padding: 10px;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  min-width: 200px;
}
.close {
  float: right;
  border: none;
  background: transparent;
  font-size: 16px;
  cursor: pointer;
}
</style>

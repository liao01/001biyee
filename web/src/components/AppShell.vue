<template>
  <div class="travel-shell">
    <the-header class="travel-shell__header" />

    <div class="travel-shell__body">
      <aside class="travel-shell__desktop-sidebar">
        <the-sider />
      </aside>

      <main class="travel-shell__content">
        <slot />
      </main>
    </div>

    <div class="travel-shell__mobile-nav">
      <the-sider mobile />
    </div>
  </div>
</template>

<script setup>
import TheHeader from './the-header.vue'
import TheSider from './the-sider.vue'
</script>

<style scoped>
.travel-shell {
  background: var(--travel-color-bg);
  min-height: 100vh;
}

.travel-shell__header {
  left: 0;
  position: fixed;
  right: 0;
  top: 0;
  z-index: 100;
}

.travel-shell__body {
  display: flex;
  min-height: 100vh;
  padding-top: var(--travel-header-height);
}

.travel-shell__desktop-sidebar {
  background: var(--travel-color-bg);
  border-right: 1px solid var(--travel-color-border);
  bottom: 0;
  left: 0;
  position: fixed;
  top: var(--travel-header-height);
  width: var(--travel-sidebar-width);
  z-index: 80;
}

.travel-shell__content {
  background: var(--travel-color-bg);
  margin-left: var(--travel-sidebar-width);
  min-width: 0;
  width: calc(100% - var(--travel-sidebar-width));
}

.travel-shell__mobile-nav {
  display: none;
}

@media (max-width: 1099px) {
  .travel-shell__desktop-sidebar {
    width: 196px;
  }

  .travel-shell__content {
    margin-left: 196px;
    width: calc(100% - 196px);
  }
}

@media (max-width: 767px) {
  .travel-shell__desktop-sidebar {
    display: none;
  }

  .travel-shell__content {
    margin-left: 0;
    width: 100%;
  }

  .travel-shell__mobile-nav {
    bottom: 0;
    display: block;
    left: 0;
    position: fixed;
    right: 0;
    z-index: 120;
  }
}
</style>

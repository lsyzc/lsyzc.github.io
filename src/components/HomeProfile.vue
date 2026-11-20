<script setup lang="ts">
import InteractiveAvatar from './InteractiveAvatar.vue'
</script>

<template>
  <div class="home-profile">
    <InteractiveAvatar class="home-avatar" />

    <header class="home-intro">
      <h1 class="home-title">
        SaturnTu
      </h1>
      <p class="home-description">
        Hey, I'm SaturnTu — a student who loves coding. Sharing what I learn, bits of life, and things I build along the way.
      </p>
      <nav class="home-links" aria-label="个人主页和联系方式">
        <a href="https://github.com/lsyzc" target="_blank" rel="noopener noreferrer">GitHub</a>
        <!-- <a href="https://leetcode.cn/u/litutu/" target="_blank" rel="noopener noreferrer">Leetcode</a> -->
        <a href="mailto:lishengyao2016@gmail.com">Email</a>
      </nav>
    </header>

    <div class="home-sections">
      <RouterLink class="home-section" to="/posts">
        <span class="home-section-label">writing</span>
        <span class="home-section-title">Blog</span>
        <span class="home-section-description">技术分享与生活记录</span>
      </RouterLink>
      <RouterLink class="home-section" to="/projects">
        <span class="home-section-label">building</span>
        <span class="home-section-title">Projects</span>
        <span class="home-section-description">我的项目与开源探索</span>
      </RouterLink>
    </div>

    <p class="home-contact">
      Say hello at
      <a href="mailto:lishengyao2016@gmail.com">lishengyao2016@gmail.com</a>
    </p>
    <p class="home-sponsor">
      If you enjoy my work,
      <a href="https://github.com/sponsors/lsyzc" target="_blank" rel="noopener noreferrer">consider sponsoring me</a>.
      Thank you!
    </p>
  </div>
</template>

<style scoped>
.home-profile {
  width: 100%;
  max-width: 40rem;
  margin: 0 auto;
  padding-top: 0.5rem;
}

.home-profile :deep(.home-avatar) {
  width: min(18rem, 70vw);
  margin: 0 auto 2rem;
}

.home-profile .home-title {
  margin: 0;
  font-size: clamp(2.5rem, 7vw, 3.5rem);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.055em;
}

.home-profile .home-description {
  max-width: 31rem;
  margin: 1.25rem 0 0;
  font-size: clamp(1.125rem, 3vw, 1.375rem);
  line-height: 1.55;
  opacity: 0.65;
  text-wrap: pretty;
}

.home-links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.5rem;
  margin-top: 1.5rem;
}

.home-profile .home-links a {
  border: 0;
  font-size: 0.9375rem;
  font-weight: 500;
  opacity: 0.7;
  transition: opacity 150ms;
}

.home-profile .home-links a:hover {
  opacity: 1;
}

.home-sections {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 3rem;
}

.home-profile .home-section {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  min-width: 0;
  padding: 1.375rem;
  border: 1px solid #8883;
  border-radius: 1.5rem;
  background: linear-gradient(180deg, #8881, #8880);
  text-decoration: none;
  transition:
    border-color 150ms,
    background-color 150ms;
}

.home-profile .home-section:hover {
  border-color: #8886;
  background-color: #8881;
}

.home-section-label {
  margin-bottom: 0.625rem;
  font-family: monospace;
  font-size: 0.875rem;
  font-weight: 400;
  opacity: 0.5;
}

.home-section-title {
  font-size: 1.125rem;
  font-weight: 600;
}

.home-section-description {
  font-size: 0.875rem;
  font-weight: 400;
  line-height: 1.6;
  opacity: 0.6;
}

.home-profile .home-contact {
  margin: 2rem 0 0;
  font-size: 0.9375rem;
  overflow-wrap: anywhere;
}

.home-profile .home-sponsor {
  margin: 0.75rem 0 0;
  font-size: 0.875rem;
  opacity: 0.55;
}

.home-profile .home-contact a,
.home-profile .home-sponsor a {
  border: 0;
  text-decoration: underline;
  text-decoration-color: #8886;
  text-underline-offset: 4px;
}

.home-profile a:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 5px;
}

@media (max-width: 480px) {
  .home-profile :deep(.avatar-bubble) {
    top: -0.75rem;
    left: 50%;
    max-width: calc(100vw - 4rem);
    translate: -50% 0;
  }
}

@media (max-width: 420px) {
  .home-sections {
    grid-template-columns: 1fr;
    gap: 0.75rem;
    margin-top: 2.25rem;
  }

  .home-profile .home-section {
    padding: 1.125rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-profile .home-links a,
  .home-profile .home-section {
    transition: none;
  }
}
</style>

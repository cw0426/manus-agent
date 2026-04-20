<template>
  <footer class="app-footer">
    <div class="footer-content">
      <div class="footer-section">
        <div class="footer-logo">
          <h3>AI超级智能体应用平台</h3>
        </div>
        <div class="footer-links">
          <a href="#">《用户协议》</a>
          <a href="#">《隐私政策》</a>
        </div>
      </div>
    </div>

    <div class="footer-bottom">
      <p>© {{ currentYear }} AI超级智能体应用平台 - 让AI为你服务</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

// 计算当前年份
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  background-color: #fff;
  padding: 20px 0;
  color: #666;
  border-top: 1px solid #eee;
  width: 100%;
  margin-top: auto;
  position: relative;
  z-index: 2;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.footer-section {
  text-align: center;
}

.footer-logo h3 {
  font-size: 1.1rem;
  margin-bottom: 10px;
  color: #333;
}

.footer-links {
  display: flex;
  justify-content: center;
  gap: 20px;
}

.footer-links a {
  color: #666;
  text-decoration: none;
  transition: color 0.2s;
}

.footer-links a:hover {
  color: #007bff;
}

.footer-bottom {
  text-align: center;
  padding-top: 15px;
  margin-top: 15px;
  border-top: 1px solid #eee;
  color: #999;
  font-size: 0.85rem;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .app-footer {
    padding: 15px 0;
  }

  .footer-logo h3 {
    font-size: 1rem;
  }

  .footer-links {
    gap: 15px;
  }

  .footer-links a {
    font-size: 0.9rem;
  }
}
</style> 
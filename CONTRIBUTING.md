# 贡献指南 (Contributing Guide)

感谢您关注并愿意为 CLaJ（Minecraft 版）做出贡献！

## 🛠 开发与构建

### 1. 环境准备
- **JDK 25** (推荐 Eclipse Temurin 25 或 OpenJDK 25)
- Git

### 2. 构建与运行测试
```bash
# 赋予执行权限
chmod +x gradlew

# 运行全套单元测试
./gradlew test

# 构建模组 Jar 与中继服务端 Jar (输出到 build/release/)
./gradlew release
```

## 📝 提交代码准则

1. **代码规范**：
   - 遵循 Java 25 最佳实践与现代语法特性。
   - 保持代码整洁，去除未使用的 import 与冗余代码。
   - 涉及协议、并发队列、内存池及编解码的代码需保证线程安全与健壮的异常处理。
2. **测试覆盖**：
   - 为新增的通用工具类或协议处理逻辑编写 JUnit 5 单元测试。
   - 提交前确保 `./gradlew test` 全部通过。
3. **提交信息**：
   - 推荐使用 Conventional Commits 风格，如 `fix: ...`、`feat: ...`、`refactor: ...`、`docs: ...`。

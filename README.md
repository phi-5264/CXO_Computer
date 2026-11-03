# 卡西欧计算器 (CasioCalc)

免费、开源、无广告的安卓计算器，忠实复刻卡西欧 **fx-991CN X** 的全部功能。

让你不再需要购买实体计算器，也不用下载市面上那些要收费 / 有广告的 App。

## 功能

对标 fx-991CN X，已实现：

| 模块 | 内容 |
|------|------|
| 基础计算 | 四则运算、括号、幂、阶乘、百分号、Ans/PreAns、M 记忆 |
| 科学函数 | sin/cos/tan（DEG/RAD/GRA）、反三角、双曲、log/ln、√/³√、10^x、e^x、倒数 |
| 复数 | 四则运算、i 虚数、模/辐角、共轭、实部/虚部 |
| 统计 | 单变量、双变量回归（r 相关系数、a/b 系数） |
| 方程 | 一元二次、三次、线性方程组 |
| 矩阵 | 加减乘、转置、行列式、逆矩阵 |
| 向量 | 点积、叉积 |
| 进制 | BASE-N、进制转换、逻辑运算 |
| 单位换算 | 10 类单位 |
| 常数 | 18 个科学常数 |
| 存储 | A–F、X、Y、M 变量，STO/RCL |
| 按键 | SHIFT/ALPHA 二级功能，还原实体布局 |

## 下载

前往 [Releases](https://github.com/phi-5264/CXO_Computer/releases) 下载最新 APK。

安装时需开启「允许安装未知来源应用」（自签名 APK）。

## 构建

```bash
# 环境：JDK 17、Android SDK (platforms;android-34, build-tools;34.0.0)
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

## 许可证

MIT

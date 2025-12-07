# 学习总结：挑战与优化思路

在 TTEdit 项目的开发过程中，我们遇到了多个技术挑战，涉及 Jetpack Compose 的状态管理、手势交互以及图像处理算法。以下是对这些挑战及相应优化思路的总结。

## 1. Jetpack Compose 状态管理陷阱

### 🔴 挑战：MutableList 内部属性变化不触发重组
**问题描述**：在调整文字图层（`TextLayer`）的属性（如字体大小、颜色）时，虽然 `MutableList` 中的对象数据确实改变了，但 UI 并没有实时刷新。
**原因分析**：Jetpack Compose 的 `mutableStateListOf` 只能监听列表项的增删改（引用的变化），无法深度监听列表项内部属性（如 `layer.fontSize`）的变化，除非该属性本身也是 `MutableState`。
**❌ 初始尝试**：直接修改对象属性 `layer.fontSize = newValue`，导致 UI 无响应。

### 🟢 优化思路：Copy-and-Replace 策略
**解决方案**：采用不可变数据的思想（Immutability）。
1.  **复制**：使用 Kotlin data class 的 `copy()` 方法创建一个属性更新后的新对象。
2.  **替换**：将列表中的旧对象替换为新对象。
```kotlin
// 错误做法
// list[index].fontSize = newSize

// 正确做法
val newLayer = list[index].copy(fontSize = newSize)
list[index] = newLayer // 触发重组
```
**收获**：在 Compose 中处理集合数据时，应优先考虑不可变数据流或确保状态更新能正确传递到 Observation Chain。

---

## 2. 复杂手势交互冲突

### 🔴 挑战：文字拖动与缩放/旋转的冲突
**问题描述**：用户在尝试双指缩放文字时，容易误触发拖动（Pan）操作；或者在选中文字后，需要先长按才能拖动，操作不流畅。
**原因分析**：
1.  `detectTransformGestures` 会消费触摸事件，如果它和 `detectDragGestures` 同时存在且没有合理处理事件分发，会导致冲突。
2.  使用了 `detectDragGesturesAfterLongPress`，导致用户必须长按才能移动，体验不佳。

### 🟢 优化思路：统一手势处理与无模态交互
**解决方案**：
1.  **合并手势**：不再区分单纯的“拖动”和“变换”。使用 `detectTransformGestures` 统一处理平移（Pan）、缩放（Zoom）和旋转（Rotation）。
2.  **移除长按限制**：允许用户直接拖动文字，无需长按选中，提升了操作的直觉性。
3.  **手势归一化**：将屏幕坐标的变化（Pan）除以当前的缩放比例（FitScale），确保在图片放大缩小后，文字的移动距离依然跟手指移动距离同步。

---

## 3. 图像处理与主线程阻塞

### 🔴 挑战：实时滤镜与旋转导致的 UI 卡顿
**问题描述**：在应用滤镜或旋转大分辨率图片时，如果直接在主线程进行 Bitmap 处理，会导致界面掉帧甚至 ANR（应用无响应）。
**原因分析**：Bitmap 的 `createBitmap` 和 `ColorMatrix` 计算是 CPU 密集型操作，耗时较长。

### 🟢 优化思路：协程异步处理
**解决方案**：
1.  **后台执行**：利用 Kotlin Coroutines 的 `viewModelScope.launch(Dispatchers.IO)` 将所有图像处理任务调度到 IO 线程池执行。
2.  **状态流更新**：处理完成后，通过 `MutableStateFlow` 更新 Bitmap，Compose 自动监听数据流变化并刷新 UI。
3.  **防抖动 (Debounce)**：对于滑动条（如亮度调节）产生的高频事件，引入简单的防抖机制（如 `delay`），避免每毫秒都触发一次重型计算。

---

## 4. 自由裁剪功能的稳定性

### 🔴 挑战：裁剪框拖动不灵敏或状态丢失
**问题描述**：在自由裁剪模式下，拖动裁剪框的边缘有时会失效，或者在快速拖动时裁剪框跟不上手指。
**原因分析**：
1.  在 Compose 的 `pointerInput` 中闭包捕获了旧的 `Rect` 状态。
2.  每次 recomposition 导致手势检测器重启。

### 🟢 优化思路：RememberUpdatedState
**解决方案**：
1.  使用 `rememberUpdatedState` 包装当前的裁剪框 `Rect`，确保在手势回调中始终能访问到最新的状态值，而无需重启手势检测。
2.  明确区分“触碰点判定”和“拖动更新”逻辑，确保手指一旦抓住边缘，在松手前都能持续控制该边缘。

---

## 5. 文字中心点对齐问题

### 🔴 挑战：文字缩放时位置偏移
**问题描述**：当改变文字大小或旋转时，文字似乎是绕着左上角或其他点变换，而不是绕着文字中心，导致视觉上的“漂移”。

### 🟢 优化思路：基于测量的精确居中
**解决方案**：
1.  **测量尺寸**：使用 `BasicText` 的 `onTextLayout` 回调获取文字渲染后的精确宽高 (`IntSize`)。
2.  **中心偏移**：在 `Modifier.offset` 中，应用 `-(width/2)` 和 `-(height/2)` 的偏移量。
3.  **结果**：确保了文字始终以其几何中心为锚点进行旋转和缩放，符合用户的心理预期。

---

## 总结

通过本项目的开发，我们深刻体会到：
*   **架构决定下限**：MVVM + Unidirectional Data Flow (单向数据流) 让状态管理更清晰，虽然在列表更新上需要小心，但总体上减少了 Bug。
*   **细节决定上限**：手势的流畅度、UI 的实时响应、图像处理的性能优化，这些细节直接决定了用户体验的层级。

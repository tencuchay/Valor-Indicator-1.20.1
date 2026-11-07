# AGENT HANDOVER & ACTIVE TASK CONTEXT: VALOR INDICATOR (1.20.1)

> **Mục đích tài liệu:** Hướng dẫn chi tiết dành cho các AI Agent (hoặc Developer) tiếp quản dự án `Valor-Indicator-1.20.1`. Tài liệu này giải thích cấu trúc hệ thống, quy ước code, cơ chế hoạt động của bảng chỉ số (`StatsScreen`), các tính năng đã phát triển (Stats Breakdown, Hover Tooltips, Side Panel) và quy trình từng bước khi thêm/sửa chỉ số hoặc tính năng UI.

---

## 1. Thông Tin Kỹ Thuật (Tech Stack & Environment)
- **Game Version:** Minecraft 1.20.1
- **Mod Loader:** Minecraft Forge `47.3.0` (Mapping: `official 1.20.1`)
- **Java:** JDK 17
- **Mod ID:** `valor_indicator`
- **Mối liên hệ giữa các dự án liên kết:**
  - `Valor-Indicator-1.20.1` (Thư mục hiện tại: `D:\Valor-Indicator-1.20.1`): Mod hiển thị UI, thanh máu/mana (`HealthBarHud`), chỉ số sát thương nổi 3D (`IndicatorRenderer`), và bảng thông số nhân vật phím `U` (`StatsScreen`).
  - `Valor-Items-1.20.1` (`D:\Valor-Items-1.20.1`): Mod logic chiến đấu, trang bị và custom attributes (`valor_items`).
  - `Valor-Weapons-1.20.1` (`D:\Valor-Weapons-1.20.1`): Mod vũ khí mở rộng.

---

## 2. Quy Tắc Ứng Xử Bắt Buộc (Tuân thủ theo `README.md`)
Bất kỳ AI Agent nào khi can thiệp codebase này đều **phải tuân thủ nghiêm ngặt 4 quy tắc**:
1. **Think Before Coding:** Nêu rõ giả định trước khi làm. Nếu có nhiều cách giải thích hoặc điểm chưa rõ, phải dừng lại hỏi người dùng trước khi code.
2. **Simplicity First:** Viết lượng code tối thiểu giải quyết đúng bài toán. Không tự tạo abstraction cho code dùng 1 lần, không tạo cấu hình thừa thãi.
3. **Surgical Changes:** Chỉ sửa đúng vị trí cần thiết. Giữ nguyên format/style xung quanh. Không tự ý refactor code không hỏng. Tự dọn sạch import/biến thừa do mình làm phát sinh.
4. **Goal-Driven Execution:** Xác định rõ tiêu chí kiểm chứng (verification criteria). Trước khi kết thúc phải chạy lệnh kiểm tra (ví dụ: `./gradlew.bat build`).

---

## 3. Kiến Trúc & Cơ Chế Hoạt Động Của Bảng Chỉ Số (`StatsScreen.java`)

### 3.1. File vị trí & Phím mở
- **File:** `src/main/java/com/tencuchay/valorindicator/client/StatsScreen.java`
- **Phím mở:** Phím `U` (được đăng ký tại `StatsKey.java`, bấm lại `U` sẽ đóng màn hình).

### 3.2. Cơ chế tra cứu thuộc tính mềm (Soft Dependency / Dynamic Lookup)
Bảng chỉ số không phụ thuộc compile-time trực tiếp vào `valor_items` mà tra cứu động qua `ForgeRegistries.ATTRIBUTES`:
```java
private static Attribute modded(String namespace, String path) {
    ResourceLocation id = new ResourceLocation(namespace, path);
    return ForgeRegistries.ATTRIBUTES.containsKey(id) ? ForgeRegistries.ATTRIBUTES.getValue(id) : null;
}
```
> **Lợi ích:** Nếu `valor_items` chưa được cài đặt, hàm trả về `null` và hiển thị giá trị mặc định `0.0`, game **không bao giờ bị crash** do thiếu class/registry.

### 3.3. Cấu trúc Record `Stat`
```java
private record Stat(String langKey, Supplier<Attribute> attribute, double scale, boolean percent, boolean breakdown) {}
```
- `langKey`: Hậu tố của key ngôn ngữ trong file lang (`stat.valor_indicator.<langKey>`).
- `attribute`: Supplier trỏ tới `Attributes.<VANILLA>` hoặc `modded("valor_items", "<name>")`.
- `scale`: Hệ số nhân trước khi hiển thị (ví dụ: `1` giữ nguyên, `100` để đổi `0.25` thành `25%`, `1000` để đổi `0.1` của movement speed thành `100`).
- `percent`: `true` nếu muốn tự động thêm ký tự `%` vào sau con số.
- `breakdown`:
  - `true`: Hiển thị chi tiết dạng `Tổng (Gốc + Thưởng)` như `301 (263+38)` ngay trên dòng (dùng cho Máu, Giáp, Công vật lý...).
  - `false`: Chỉ hiển thị giá trị tổng (nếu có thay đổi so với giá trị cơ bản thì chữ số sẽ tự động đổi màu xanh lá).

### 3.4. Hệ Thống Stat Breakdown Kép (Hover Tooltip + Click Side Panel Nâng Cao)
1. **Hover Tooltip (Rê chuột - Xem nhanh):**
   - Khi hover qua bất kỳ dòng chỉ số nào, dòng đó được highlight viền xanh xám (`0x664A5A8C`).
   - Tooltip nổi (`renderStatTooltip`) hiển thị:
     - Tên chỉ số (vàng đậm).
     - Giá trị cơ bản (`Base: %s`).
     - Danh sách tóm tắt các `AttributeModifier` đang hoạt động (`+X từ Trang bị/Hiệu ứng`).
     - Tổng chỉ số (`Total: %s`).
     - Gợi ý: *"Nhấp chuột để ghim chi tiết"*.
2. **Click Side Panel (Nhấp chuột - Ghim & Cuộn xem chi tiết):**
   - Click vào bất kỳ dòng nào sẽ ghim (`selectedStat = stat`), dòng đổi sang viền vàng neon (`0xFFE0C068`).
   - Mở **Bảng chi tiết phụ (Side Panel)** bên phải bảng chính (chiều rộng 240px, tự động co giãn toạ độ để không tràn màn hình):
     - Tiêu đề & Nút đóng `✕` (hoặc click lại vào dòng đang chọn để đóng).
     - Tổng kết Giá trị cuối (`Final Value`) và Giá trị gốc (`Base Value`).
     - Danh sách đầy đủ từng `AttributeModifier`: phân biệt phép cộng phẳng `+X`, nhân cơ bản `+X% (Base)` và nhân tổng `+X% (Total)`.
     - **Texture Icon 16x16 bên cạnh mỗi dòng:** Render trực tiếp item icon của `valor_items` (Thương Xuyên Phá, Phù Chú Trường Sinh, Gươm Sấm Sét...) hoặc item vanilla (Vũ khí, Giáp, Bình kinh nghiệm cho cấp độ...).
     - **Cơ chế Cuộn (Mouse Scroll & Scissor Clipping):** Sử dụng `g.enableScissor()` và `mouseScrolled()` kết hợp thanh cuộn (scrollbar) để cuộn xem mượt mà khi có nhiều chỉ số cộng dồn mà không bị tràn panel.
     - **Hệ thống nhận diện tên thông minh (`resolveModifier`):** Tự động nhận diện chính xác toàn bộ 15 Relic của `valor_items` và các modifier đặc thù theo cơ chế:
       - **Thánh Kiếm (Claves Sancti):** Nhận diện cả `infinite_crit_rate`, `infinite_crit_damage` (nội tại Vô Hạn cộng Crit Rate & Crit Damage) và `gift_of_the_swift` (Khinh Tốc) $\to$ hiển thị icon Thánh Kiếm và tên **`Thánh Kiếm (Vô Hạn)`** / **`Thánh Kiếm (Khinh Tốc)`**.
       - **Thương Xuyên Phá (Rankbreaker):** Nhận diện `break` (Nghiền Ép), `wildwalk` (Khinh Công) $\to$ icon Thương Xuyên Phá và tên tương ứng.
       - **Gươm Uriel (Uriel's Brand):** Nhận diện `magic_guardian_speed` (Bảo Hộ), `devils_bane` (Chống Quỷ) $\to$ icon Gươm Uriel.
       - **Lưỡi Hái Đoạt Mệnh (Death Sickle):** Nhận diện `death_sickle_move`, `death_sickle_attack` $\to$ icon Lưỡi Hái Đoạt Mệnh.
       - Tự động đồng bộ chuẩn theo ngôn ngữ trong game (Tiếng Việt hoặc Tiếng Anh).

---

## 4. Chi Tiết Trạng Thái Code Hiện Tại (2026-11-07)

### 4.1. Bố cục 2 Cột Đối Xứng (7 hàng mỗi bên):
| Hàng | Cột 1 (`COLUMN_1`) | Cột 2 (`COLUMN_2`) |
| :--- | :--- | :--- |
| 1 | Công vật lý (`ATTACK`) | Công phép (`MAGIC_POWER`) |
| 2 | Máu tối đa (`MAX_HEALTH`) | Năng lượng tối đa (`MAX_MANA`) |
| 3 | Giáp (`ARMOR`) | Giáp phép (`MAGIC_ARMOR`) |
| 4 | Tốc đánh (`ATTACK_SPEED`) | Tốc chạy (`MOVE_SPEED`) |
| 5 | Tỷ lệ chí mạng (`CRIT_RATE`) | Sát thương chí mạng (`CRIT_DAMAGE`) |
| 6 | Hồi máu / 5s (`HEALTH_REGEN`) | Hồi năng lượng / 5s (`MANA_REGEN`) |
| 7 | Sát lực (`LETHALITY`) | Xuyên giáp (%) (`ARMOR_PENETRATION_PERCENT`) |

### 4.2. Danh sách Key Ngôn Ngữ Bổ Trợ (Localization):
Cả hai file `en_us.json` và `vi_vn.json` đã được trang bị đầy đủ:
- `stat.valor_indicator.lethality`
- `stat.valor_indicator.armor_penetration_percent`
- `stat.valor_indicator.breakdown.title`
- `stat.valor_indicator.breakdown.base` / `stat.valor_indicator.breakdown.total`
- `stat.valor_indicator.breakdown.base_label` / `stat.valor_indicator.breakdown.total_label`
- `stat.valor_indicator.breakdown.modifiers` / `stat.valor_indicator.breakdown.no_modifiers`
- `stat.valor_indicator.breakdown.hint`
- `stat.valor_indicator.breakdown.mod.*` (`weapon`, `armor`, `toughness`, `tool`, `curios`, `level`, `unknown`)

---

## 5. Quy Trình Chuẩn Dành Cho Agent Khi Muốn Thêm / Sửa Đổi Chỉ Số Bảng Stats

Khi nhận nhiệm vụ thêm một chỉ số mới vào bảng stats:

### Bước 1: Tra cứu thông tin Attribute nguồn
- Mở `D:\Valor-Items-1.20.1\src\main\java\com\valor\items\registry\ModAttributes.java` để xem:
  - Tên registry (ví dụ `"lifesteal"`, `"magic_shield"`).
  - Khoảng giá trị min/max và default value (để quyết định `scale`, `percent` và `breakdown`).

### Bước 2: Khai báo `Stat` trong `StatsScreen.java`
- Thêm định nghĩa hằng số:
  ```java
  private static final Stat TÊN_STAT = new Stat("ten_stat", () -> modded("valor_items", "ten_stat"), scale, isPercent, showBreakdown);
  ```

### Bước 3: Đưa vào `COLUMN_1` hoặc `COLUMN_2`
- Sắp xếp cân đối 2 cột để số hàng bằng nhau.

### Bước 4: Khai báo bản dịch trong file Lang
- Thêm key vào cả 2 file:
  - `src/main/resources/assets/valor_indicator/lang/en_us.json`
  - `src/main/resources/assets/valor_indicator/lang/vi_vn.json`
- Format key: `"stat.valor_indicator.ten_stat": "Tên Hiển Thị"`

### Bước 5: Build và kiểm chứng
- Chạy: `./gradlew.bat build` (với `BypassSandbox: true`).
- Đảm bảo kết quả là `BUILD SUCCESSFUL` với `0 errors`.

---

## 6. Lưu Ý Quan Trọng Về Môi Trường Build
- Trên Windows, Gradle Wrapper sử dụng cache tại `D:\Gradle`.
- Khi chạy lệnh Gradle trong môi trường Sandbox của Antigravity, file khóa `.zip.lck` có thể bị lỗi `Access is denied` (EPERM).
- **Giải pháp:** Phải chạy lệnh với chế độ bypass sandbox (`BypassSandbox: true`).

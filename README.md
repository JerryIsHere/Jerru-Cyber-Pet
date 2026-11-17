# Jerru Cyber-Pet

Jerru là một người bạn ảo vẽ bằng Canvas, chạy trong lớp phủ trên Android 8.0 trở lên.

## Build

Cần JDK 17 và Android SDK 35. Chạy `./gradlew assembleDebug`; APK nằm tại
`app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions cũng build và tải
artifact `Jerru.apk` cho mỗi lần push.

## Sử dụng

1. Mở ứng dụng, chọn **Allow overlay** và cấp quyền hiển thị trên ứng dụng khác.
2. Chọn **Start Jerru**. Dùng nút **Stop Jerru** hoặc thông báo của dịch vụ để dừng.
3. Tùy chọn **Enable face reactions** và cho phép camera khi ứng dụng đang mở.
   Camera trước được phân tích trên thiết bị với tốc độ tối đa khoảng 1–2 FPS;
   không lưu hoặc tải ảnh lên. Chọn **Disable face reactions** để tắt.
4. Tùy chọn **Notification access** để bật phản ứng với thông báo. Ứng dụng
   chỉ sử dụng thời điểm nhận thông báo, không lưu nội dung.

Lớp phủ không nhận thao tác chạm để không cản trở ứng dụng bên dưới. Hoạt động
chạy nền, vị trí cutout và camera phụ thuộc giới hạn của phiên bản Android và
nhà sản xuất; camera không tự khởi động lại sau khi dịch vụ bị dừng.
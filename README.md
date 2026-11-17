# Jerru Cyber-Pet

Jerru là người bạn ảo có đôi mắt Cyan vẽ bằng Canvas, tìm vùng tối trên màn
hình để ẩn nấp. Tính năng này yêu cầu Android 11 (API 30) trở lên.

## Build và tải APK

Cần JDK 17 và Android SDK 35. Chạy `./gradlew assembleDebug`; APK nằm tại
`app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions tải lên artifact
`app-debug.apk` và tạo Release cho mỗi commit trên `main`.

Tải APK mới nhất từ mục **Releases**, hoặc từ
`https://github.com/JerryIsHere/Jerru-Cyber-Pet/releases/latest/download/app-debug.apk`.
Artifact trong tab **Actions** cần đăng nhập GitHub để tải.

## Sử dụng và quyền riêng tư

1. Chọn **Enable Jerru accessibility**, bật dịch vụ Jerru trong cài đặt trợ năng,
   rồi quay lại và chọn **Start Jerru**. Dùng **Stop Jerru** hoặc thông báo
   để dừng.
2. Dịch vụ trợ năng chụp màn hình khoảng mỗi 3 giây và khi nội dung thay đổi
   để tìm vùng tối. Ảnh chỉ được phân tích trên thiết bị, không lưu hoặc tải lên;
   những màn hình bảo mật có thể không cho phép chụp và Jerru sẽ ẩn đi.
   Khi không có vùng tối liên tục 10–15 giây, Jerru xuất hiện trong vũng tối
   trong khoảng 4,5 giây.
3. Tùy chọn **Enable face reactions** và cho phép camera khi ứng dụng đang mở.
   Camera trước được phân tích trên thiết bị; ảnh không lưu hoặc tải lên. Chọn
   **Disable face reactions** để tắt. Camera không tự bật lại sau khi dừng.

Lớp phủ không nhận thao tác chạm. Cảm biến ánh sáng, gia tốc và trạng thái sạc
được sử dụng để điều khiển biểu cảm; hoạt động nền phụ thuộc thiết bị. Tùy chọn
**Notification access** cho phản ứng với thông báo chỉ dùng thời điểm nhận,
không lưu nội dung thông báo.

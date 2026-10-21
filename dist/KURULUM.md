# Ders Zili test kurulumu

## 1. Telefona kur

`ders-zili-telefon-debug.apk` dosyasını Samsung telefonuna gönderip aç. Android isterse kullandığın dosya yöneticisi için **Bilinmeyen uygulamaları yükle** iznini ver.

## 2. Galaxy Watch'a kur

1. Saatte **Ayarlar → Saat hakkında → Yazılım bilgileri** ekranını aç.
2. **Yazılım sürümü** satırına birkaç kez dokunup geliştirici modunu aç.
3. **Ayarlar → Geliştirici seçenekleri** içinden **ADB hata ayıklama** ve **Kablosuz hata ayıklama** seçeneklerini aç.
4. Bilgisayar ile saatin aynı Wi‑Fi ağında olduğundan emin ol.
5. Android Studio'da saati **Pair Devices Using Wi‑Fi** ile eşleştir veya terminalde saatin gösterdiği eşleştirme adresiyle `adb pair IP:PORT` komutunu çalıştır.
6. Ardından `adb connect IP:PORT` ve `adb install ders-zili-saat-debug.apk` komutlarını çalıştır.

## 3. İlk kullanım

1. Saatte **Ders Zili** uygulamasını aç.
2. Bildirim iznini ver. Kesin alarm izni otomatik uygulanır.
3. Telefonda Ders Zili'ni aç, derslerini ekle ve **Eşitle** düğmesine bas.
4. Saat uygulamasını tekrar açtığında bugünkü program görünür.

Bu APK'lar test amaçlı debug imzası taşır. Google Play'e yüklemek için release imzalı App Bundle üretilmelidir.

# Ders Zili

Telefon üzerinden haftalık ders programı oluşturup Galaxy Watch / Wear OS saate gönderen yerel Android uygulaması.

## Özellikler

- Telefonda ders ekleme, düzenleme ve silme
- Bildirimin dersten kaç dakika önce geleceğini seçme
- Programı Wear OS saate Google Play Services Data Layer ile aktarma
- Saatte bugünün programı, mevcut ders ve sıradaki ders görünümü
- Ders başlamadan önce ve ders bitince titreşimli saat bildirimi
- İnternet olmadan çalışma; program saatin kendi belleğinde saklanır
- Telefon veya saat yeniden başlatıldığında alarmları yeniden kurma
- Saat bağlı değilken değişiklikleri kuyrukta tutma ve bağlantı durumunu dürüstçe gösterme
- Bozuk veya eski eşitleme verisine karşı son geçerli programı koruma
- Aynı güne çakışan ders saatlerini kaydetmeden önce uyarma

## Sürüm 1.1.0

- İlk açılışta boş programın saatteki mevcut programın üzerine yazması engellendi.
- Saat en yeni geçerli programı seçiyor; bozuk ve eski veriler yoksayılıyor.
- Hafta sonu açılışında doğru gün seçiliyor.
- Alarm yenilemesi yayın alıcısını bloklamadan arka planda yapılıyor.
- Model testleri ve GitHub Actions doğrulaması eklendi.

## Sürüm 1.2.0

- Telefonda ve saatte mevcut ya da sıradaki derse canlı geri sayım eklendi.
- Yeni ders saati, seçili günün son dersinden sonra otomatik hazırlanıyor.
- Dersleri tek dokunuşla başka güne kopyalama ve hızlı ders adı/süre seçenekleri eklendi.
- Uyarı zamanı seçenekleri 0–60 dakika aralığında genişletildi.
- Kısa, normal, güçlü ve sessiz titreşim profilleri eklendi.
- Telefon ve saat arayüzleri daha okunaklı kartlar ve belirgin durum renkleriyle yenilendi.

## Projeyi açma

1. Android Studio'da bu klasörü açın.
2. Android SDK 35 kurulmasını bekleyin.
3. Önce `mobile`, sonra `wear` yapılandırmasını eşleşmiş telefon ve saate yükleyin.
4. Saatte bildirim iznini verin. Uygulama isterse kesin alarm iznini saat ayarlarından açın.

Telefon ve saat uygulamalarının `applicationId` değerleri aynı kök paketi kullanır; Data Layer eşleşmesi için iki APK'nın aynı imzayla kurulması gerekir. Android Studio'dan çalıştırıldıklarında bu otomatik olarak sağlanır.

## Hazır test APK'ları

Derlenmiş test paketleri `dist/` klasöründedir:

- `ders-zili-telefon-debug.apk`
- `ders-zili-saat-debug.apk`

Telefona ilk APK'yı normal şekilde kurun. Saat APK'sını Galaxy Watch'ta **Ayarlar → Saat hakkında → Yazılım bilgileri** içinden yazılım sürümüne birkaç kez dokunarak geliştirici seçeneklerini açtıktan sonra kablosuz ADB ile yükleyebilirsiniz. Ayrıntılı adımlar `dist/KURULUM.md` dosyasındadır.

## Modüller

- `mobile`: Telefon arayüzü ve saat senkronizasyonu
- `wear`: Galaxy Watch / Wear OS arayüzü ve saat bildirimleri
- `shared`: Program modeli, yerel kayıt, alarm planlama ve bildirim mantığı

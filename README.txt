r201-profile-flight-smooth
==========================

Замени файлы в своём проекте:

  app/build.gradle.kts
  app/src/main/java/com/letify/app/ui/LetifyApp.kt
  app/src/main/java/com/letify/app/ui/screens/HomeScreen.kt

Потом:
  ./gradlew :app:assembleRelease

Фиксы:
  - лаг открытия профиля (progress больше не рекомпозит экраны каждый кадр)
  - карточки метрик не прыгают в начало
  - Lottie-эмодзи не мигают
  - ник выравнивается по реальному TextMeasurer
  - полёт авы не тронут

versionCode 201, versionName r201-profile-flight-smooth

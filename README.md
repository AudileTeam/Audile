> [!WARNING]
> **Free and Open-Source Android is under threat.**
>
> Google will turn Android into a locked-down platform, restricting your essential freedom to install apps of your choice. Make your voice heard.
>
> [**Keep Android Open**](https://keepandroidopen.org/).

# Audile

Audile is an open-source music recognition app that can help you quickly and accurately identify a music track playing near you.
The app integrates [AudD](https://audd.io/), [ACRCloud](https://www.acrcloud.com/), and [Shazam](https://www.shazam.com/company/) for song identification, and uses [Odesli](https://odesli.co/) to retrieve platform-specific track links.

[<img src="./img/get-it-on-f-droid.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/en/packages/com.mrsep.musicrecognizer/)
[<img src="./img/get-it-on-github.png" alt="Get it on GitHub" height="80">](https://github.com/AudileTeam/Audile/releases/)

F-Droid releases cannot be upgraded to other releases as they are compiled and signed by [F-Droid](https://f-droid.org/docs/).

The app runs on Android 8.0 or later.

## Features

* **Recognition** - Audile allows you to perform song recognition in one click. The app will save the recording if there is no internet connection, and the recognition process will resume when you come back online. You can customize the default behavior for unsuccessful recognition (no internet, no matches, another failure) via the preferences.
* **Background Usage** - Use the app seamlessly from the notification drawer, home screen widget, floating button, or quick settings tile. Manage the recognition process and obtain track information without launching the app.
* **Track Information** - Upon successful recognition, the app provides information about the track, including the title, artist, album, release year, artwork, lyrics, and links to the track on popular music services.
* **Library** - All recognized tracks are stored in the app library. You can filter the track list, create a favorites collection, search for tracks, or delete tracks.
* **Preferences** - The app offers various options to customize its behavior and appearance, accessible from the settings screen.

## Screenshots
[<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/00.png" width=200>](./fastlane/metadata/android/en-US/images/phoneScreenshots/00.png "Recognition screen")
[<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width=200>](./fastlane/metadata/android/en-US/images/phoneScreenshots/01.png "Track screen")
[<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width=200>](./fastlane/metadata/android/en-US/images/phoneScreenshots/02.png "Library screen")
[<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width=200>](./fastlane/metadata/android/en-US/images/phoneScreenshots/03.png "Notification service")
[<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width=200>](./fastlane/metadata/android/en-US/images/phoneScreenshots/04.png "Lyrics screen")
[<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width=200>](./fastlane/metadata/android/en-US/images/phoneScreenshots/05.png "Library search")
[<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width=200>](./fastlane/metadata/android/en-US/images/phoneScreenshots/06.png "Queue screen")
[<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width=200>](./fastlane/metadata/android/en-US/images/phoneScreenshots/07.png "Preferences screen")

## API Key

<details><summary>AudD</summary><p>

AudD is a paid service that requires an API token. If you don't have one, you can [sign up](https://dashboard.audd.io/) for a 14-day trial token.

You can also use the app without a token, but this will significantly restrict the number of daily recognitions that can be performed. Please keep in mind that this behavior is not guaranteed by the service and can be restricted at any time.

</p></details>

<details><summary>ACRCloud</summary><p>

ACRCloud is a paid service that offers a free trial period and ongoing limited free usage for development. To use this service, you must create an account and register a project. If you encounter any difficulties, please refer to this [guide](https://docs.acrcloud.com/tutorials/recognize-music/).

Please pay attention to these steps:
* Select the geographical region closest to you to minimize internet latency.
* Set the audio source as `Recorded audio`.
* Set the audio engine of your choice. `Audio fingerprinting` is the most accurate for general use.
* Check all the boxes for 3rd party ID integration.

</p></details>

## Contributing

Contributions are welcome and encouraged. Please read [Contributing Guidelines](https://github.com/AudileTeam/Audile/blob/master/CONTRIBUTING.md) to get started.

## Translation

[<img src="https://hosted.weblate.org/widget/audile/multi-auto.svg" alt="Translation progress">](https://hosted.weblate.org/engage/audile/)

If you would like to help translate the app into your language, you can do so through [Weblate](https://hosted.weblate.org/engage/audile/).

Feel free to submit new translations, correct errors, or suggest better phrasing for existing strings.

## Donation
This app is completely free. It comes without any ads or other forms of commercial monetization. To support ongoing development or simply say thanks, consider making a [donation](https://github.com/AudileTeam/Audile/blob/master/FUNDING.md).

## SHA-256 certificate fingerprints:

**Package ID:** com.mrsep.musicrecognizer

**GitHub APK:**
```
A5:A1:DC:1D:78:D7:F0:3A:95:36:51:B2:7D:3A:07:C2:5E:3A:F0:13:27:F4:5C:DF:86:19:A2:23:B7:03:57:D0
```

## Building from sources
Please refer to the [detailed build instructions](https://github.com/AudileTeam/Audile/blob/master/BUILDING.md).

## License

```
Copyright (C) 2023-2026 Aleksey Saenko

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

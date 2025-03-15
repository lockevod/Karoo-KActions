# kNotify - Karoo Notification Extension

This extension for Karoo devices allows you to send automatic WhatsApp notifications to selected contacts when you start, pause, resume, or finish a bike ride.

Compatible with Karoo 2 and Karoo 3 devices running Karoo OS version 1.524.2003 and later.

<a href="https://www.buymeacoffee.com/enderthor" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174"></a>

## Installation

### For Karoo 2:

1. Download the APK from the releases.
2. Prepare your Karoo for sideloading by following the [step-by-step guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html) by DC Rainmaker.
3. Install the app using the command `adb install knotify.apk`.

### For Karoo 3 (v > 1.527):

1. Open the APK download link from your mobile device.
2. Share the file with the Hammerhead Companion app.
3. Install the app through the Hammerhead Companion app.

## Whapi API Configuration

kNotify uses the Whapi service to send WhatsApp messages without requiring your phone to be connected. To set this up:

1. **Create a Whapi account**:
  - Visit [Whapi.io](https://whapi.io/) and register for an account.
  - The free plan allows sending up to 20 messages per day, which is sufficient for most users.

2. **Get your API Key**:
  - Once registered, go to the "API Keys" section in your dashboard.
  - Create a new API Key or copy the existing one.

3. **Configure kNotify**:
  - Open kNotify on your Karoo.
  - Paste your API Key in the "WHAPI API Key" field.
  - Settings are saved automatically.

4. **Test the connection**:
  - Enter a test phone number (format: 34675123123, without the '+' sign).
  - Click "Send test message" to verify everything works correctly.

## Usage

1. **Configure notification options**:
  - Enable or disable notifications for different events: ride start, end, pause, and resume.

2. **Configure phone numbers**:
  - Add up to 3 phone numbers that will receive notifications.
  - Numbers must be entered without the '+' sign and with country code (example: 34675123123).

3. **Customize messages**:
  - Personalize messages for each event type (start, end, pause, resume).
  - Messages can include custom text according to your preferences.

4. **During your ride**:
  - Once configured, kNotify will automatically send messages when you start, pause, resume, or end a ride on your Karoo.
  - No need to interact with the app during your ride; everything works in the background.

## Features

- Automatic notification sending to preset numbers.
- Customizable messages for each event type.
- Control of minimum time between messages of the same type (3 minutes by default).
- Support for multiple languages (English and Spanish).
- Integration with Karoo Live for real-time tracking.

## Known Issues

- Notifications may be delayed if there are connectivity issues.
- Your Karoo needs internet connection for messages to be sent successfully.
- The free Whapi service has a limit of 20 messages per day.
- Make sure you have sufficient balance in your Whapi account if using a paid plan.

## Privacy

kNotify does not store or share any personal information beyond what's necessary for its operation. Phone numbers and messages are stored only on your Karoo device.

## Credits

- Developed by EnderThor.
- Uses the Whapi API for message sending.
- Uses the Karoo Extensions Framework developed by Hammerhead.

## Useful Links

- [Whapi Documentation](https://docs.whapi.io/)
- [Karoo Extensions Framework](https://github.com/hammerheadnav/karoo-ext)
- [Whapi Privacy Policy](https://whapi.io/privacy-policy)
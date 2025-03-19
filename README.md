# KActions - Notification Extension for Karoo

This extension for Karoo devices allows you to send automated notifications when you start or finish a bike ride. Now compatible with multiple messaging providers and with webhook support (send custom actions from datafield).

Compatible with Karoo 2 and Karoo 3 running Karoo OS version 1.524.2003 and later.

<a href="https://www.buymeacoffee.com/enderthor" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174"></a>

## Installation

### For Karoo 2:

1. Download the APK from the releases section.
2. Prepare your Karoo for sideloading by following [DC Rainmaker's step-by-step guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).
3. Install the application with the command `adb install kactions.apk`.

### For Karoo 3 (v > 1.527):

1. Open the APK download link from your mobile device.
2. Share the file with the Hammerhead Companion app.
3. Install the app through Hammerhead Companion.

## Supported Messaging Providers

KActions supports several providers to send notifications:

### WhatsApp (WHAPI)

1. **Create a Whapi account**:
   - Visit [Whapi.io](https://whapi.io/) and register.
   - Whapi isn't free but they've sandbox and it's free (aroung 1000 whats/month). Please read whapi conditions because you've to link a real number if you use a sandbox. If you don't understant it, please don't use it.

2. **Configure KActions**:
   - Open KActions on your Karoo.
   - Select "WhatsApp (WHAPI)" as the provider.
   - Paste your API Key in the corresponding field.

### WhatsApp (CallMeBot)

1. **Get your CallMeBot API Key**:
   - Visit [CallMeBot](https://www.callmebot.com/blog/free-api-whatsapp-messages/) and follow the instructions.
   - CallMeBot is free and you don't need to link your number (only has to send a message and you'll have the key code) but you can only use with one number (destination number).

2. **Configure KActions**:
   - Open KActions on your Karoo.
   - Select "WhatsApp (CallMeBot)" as the provider.
   - Enter your CallMeBot API Key.

### SMS (TextBelt)

1. **About TextBelt**:
   - TextBelt offers worldwide SMS sending.
   - The free version allows 1 SMS per day. 
   - For more messages, you need to purchase credits at [TextBelt](https://textbelt.com/).
   - You'll receive from different numbers (TextBelt has a significant pool number)

2. **Configure TextBelt in KActions**:
   - Select "SMS (TextBelt)" as the provider.
   - To use the free version, leave the API Key field empty or type "textbelt".
   - If you've purchased credits, enter your API Key.

### Email (Resend)

1. **Create a Resend account**:
   - Visit [Resend.com](https://resend.com) and register. Resend has a free tier, you can register an own domain (with free tier) and send from this domain, but if you don't have you'll receive mails from resend.dev
   - Get your API Key from the dashboard.

2. **Configure Resend in KActions**:
   - Select "Email (Resend)" as the provider.
   - Enter your API Key.
   - Configure the sender and recipient email addresses.

## Webhook Configuration

KActions allows you to configure webhooks to send data to external services when events occur during your ride:

1. **Enable webhooks**:
   - In the webhook configuration screen, activate the "Enable webhook" option.
   - Enter a name to identify your webhook.

2. **Configure URL and data**:
   - Enter the URL of the service that will receive the data.
   - Define the POST request body in JSON format.

3. **Triggering events**:
   - Select which events will trigger the webhook: start, pause, resume, or end of ride.

4. **Location filter (optional)**:
   - You can configure the webhook to activate only when you're at a specific location.

5. **Webhook testing**:
   - Use the "Send test to webhook" function to verify the configuration.

## Usage

1. **Configure notification options**:
   - Enable or disable notifications for different events: start, end, pause, and resumption of the ride.

2. **Configure phone numbers**:
   - Add one phone numbers that will receive notifications.
   - Numbers should be entered with E164 format (with the '+' sign and with the country code example: +34675123123).

3. **Customize messages**:
   - Personalize messages for each type of event (start, end, pause, resumption).

4. **During your ride**:
   - Once configured, KActions will automatically send messages when you start, pause, resume, or end a ride on your Karoo.
   - You don't need to interact with the app during the ride; everything works in the background.

## Features

- Automatic sending of notifications to preset number.
- Customizable messages for each type of event.
- Control of minimum time between messages of the same type.
- Support for multiple languages.
- Integration with Karoo Live for real-time tracking.
- Multiple messaging providers (WhatsApp, SMS, email).
- Webhooks for integration with external services.

## Known Issues

- Notifications may be delayed if there are connectivity issues.
- Your Karoo needs an internet connection for messages to be sent correctly.
- Each provider has its own usage limitations, especially in free plans.
- Make sure you have sufficient balance if using paid plans.

## Privacy

KActions does not store or share personal information beyond what is necessary for its operation. Phone numbers and messages are stored only on your Karoo device.

## Credits

- Developed by EnderThor.
- Uses Whapi, CallMeBot, TextBelt, and Resend APIs for message sending.
- Uses the Karoo Extensions Framework developed by Hammerhead.

## Useful Links

- [Whapi Documentation](https://docs.whapi.io/)
- [CallMeBot API](https://www.callmebot.com/blog/free-api-whatsapp-messages/)
- [TextBelt SMS API](https://textbelt.com/)
- [Resend Email API](https://resend.com)
- [Karoo Extensions Framework](https://github.com/hammerheadnav/karoo-ext)

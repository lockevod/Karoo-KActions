# KActions - Notification Extension for Karoo

> [!WARNING]  
> This app is currently in prototype stage and its main features might not work at all. If you want to test it anyway and encounter issues, please report them in the github issues, ideally with adb logs attached.
> Please we careful if you use webhooks, you can trigger actions with this extension and you need to be careful with this.

This extension for Karoo devices allows to perform some automatic actions:
- Send automated notifications when you start or finish a bike ride. Now compatible with multiple messaging providers.
- Execute automated actions (webhook)
- Execute custom custom action (webhook) from custom datafield.

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

## Notifications Configuration

KActions can send automatic messages when you start/stop your ride. You need to have connected your Karoo with the mobile app (Karoo 3) or have a data connection (Karoo 2).

You need to configure basic information in Conf. Tab:

1. **Configure notification options**:
   - Enable or disable notifications for different events: start, end of the ride.
   - You've configure this for notifications and webhooks separately.

2. **Configure phone number**:
   - Add one phone numbers that will receive notifications.
   - Number should be entered with E164 format (with the '+' sign and with the country code example: +34675123123).

3. **Customize messages**:
   - Personalize messages for each type of event.
   
4. **Select actions**:
    - Start, stop or both.
    - Enable only in outdoors activities (only karoo > 1.548)

5. **Select time betweem messages**:
    - You can configure time between two messages, this is for avoid to receive several messages, you can configure it but minimum time is 5 minutes (internal delay).

6. **Live Track**:
   - Fill Live Track Karoo key. If you fill this, you will receive the link with your tracking info you need to fill only the key. For example:
   https://dashboard.hammerhead.io/live/3738Ag, please fill only 3738Ag

7. **During your ride**:
   - Once configured, KActions will automatically send messages/execute webhook when you start or end a ride on your Karoo.
   - You don't need to interact with the app during the ride; everything works in the background.

Webhook has can be triggered pressing the button in the app if you add webhook custom field in the profiles screen. 
This button has a security measure, you have to press twice to send the webhook (and you can configure to be triggered only if you're near home)

KActions supports several providers to send notifications, you need to configure before use the app:

### WhatsApp (CallMeBot) Preferred option (easy)

1. **Get your CallMeBot API Key**:
   - Visit [CallMeBot](https://www.callmebot.com/blog/free-api-whatsapp-messages/) and follow the instructions.
   - CallMeBot is free and you don't need to link your number (only has to send a message and you'll have the key code) but you can only use with one number, then you've to generate the key from the destination number (where you want to receive notifications).
   - If you want you can app the Callmebot number in your contacts with a more friendly name. Please, you can do this steps in receiver mobile not in yours.

2. **Configure KActions**:
   - Open KActions on your Karoo.
   - Select "WhatsApp (CallMeBot)" as the provider.
   - Enter your CallMeBot API Key.
   - Test CallMeBot configuration with Test Button.
  
### SMS (TextBelt). Second preferred option

1. **About TextBelt**:
   - TextBelt offers worldwide SMS sending.
   - The free version allows 1 SMS per day. This is easy but you can only send a sms and if there is some problem you cannot retry same day.
   - For more messages, you need to purchase credits at [TextBelt](https://textbelt.com/). 
   - You'll receive from different numbers (TextBelt has a significant pool number)

2. **Configure TextBelt in KActions**:
   - Select "SMS (TextBelt)" as the provider.
   - To use the free version, leave the API Key field empty or type "textbelt".
   - If you've purchased credits, enter your API Key.
   - Test Textbelt configuration with Test Button.

### WhatsApp (WHAPI) Complicated option but powerful.

1. **Create a Whapi account**:
   - Visit [Whapi.io](https://whapi.io/) and register.
   - Whapi isn't free but they've sandbox and it's free (aroung 1000 whats/month). Please read whapi conditions because you've to link a real number if you use a sandbox. If you don't understand it, please don't use it. It's easy but you need to understand risks. I will not provide more info about enrollment process.

2. **Configure KActions**:
   - Open KActions on your Karoo.
   - Select "WhatsApp (WHAPI)" as the provider.
   - Paste your API Key in the corresponding field.
   - Test Whapi configuration with Test Button.

## Webhook Configuration

KActions allows you to configure webhooks to execute external services when events occur during your ride (start/stop) or you can also execute it if you add webhook custom field in your profile. Webhook configuration is only for advanced users, it's very powerful (because you can execute action you want) but it's necessary you have experience. 

1. **Enable webhooks**:
   - In the webhook configuration screen, activate the "Enable webhook" option.
   - Enter a name to identify your webhook.

2a. **API/Webhook information**:
   - You need to have information about your service (url, post and headers)
   - Please check this info before next steps. You can use postman or other chrome extensions to check the correct behaviour.

2b. **Configure URL, data and headers**:
   - Enter the URL of the service that will receive the data.
   - Define the POST request body in JSON format.
   - Define Headers in JSON format.

3. **Triggering events**:
   - Select which events will trigger the webhook: start or end of ride.

4. **Location filter (optional)**:
   - You can configure the webhook to activate only when you're at at home location (POI Home in your Karoo).

5. **Webhook testing**:
   - Use the "Send test to webhook" function to verify the configuration.


## Import/Export API keys/Webhook Configuration

KActions allows you to import both provider configurations and webhook settings directly from a file. This is very useful if you've to introduce a complicated key or you webhook is complicated. You'll need to connect your Karoo by cable and execute adb commands.

- **Import Provider Settings**:
   - Fill information in file (you've a template in templates folder. Please don't modify structure, change/complete only apikey (insert your apikey for your provider (between quotation marks). You don't need to fill a key for every provider. You can do copy with adb with the command:
   ```
   adb push provider_config.json /sdcard/Android/data/com.enderthor.kActions/files/
   ```
   - Copy the file to your Karoo device in the KActions folder (/sdcard/Android/data/com.enderthor.kActions/files/). You need to create this folder if it doesn't exist (you can use a file manager app or ADB).
   - Please respect the file name, it must to be "provider_config.json"
   - Access the Provider Configuration screen.
   - Tap the "Import Configuration" button.
   - The app will read settings from file.

- **Import Webhook Settings**:
     - Fill information in file (you've a template in templates folder. Please don't modify structure, change/complete only second position, for example, don't change "name", insert name after : between  quotation marks. You don't need to fill all fields.
     - Copy the file to your Karoo device in the KActions folder (/sdcard/Android/data/com.enderthor.kActions/files/). You need to create this folder if it doesn't exist (you can use a file manager app or ADB). You can do copy with adb with the command:
     ```
     adb push webhook_config.json /sdcard/Android/data/com.enderthor.kActions/files/
     ```
     - Please respect the file name, it must to be "webhook_config.json"
     - Access the Webhook Configuration screen.
     - Tap the "Import Webhooks" button.
     - The app will read webhook configurations from the previously exported file.

- This feature makes it easy to import api keys and webhooks information (but you and introduce directly by Karoo keyboard). Be careful, this file has sensitive information (api keys, phone numbers, etc.) and you should not share it with anyone.
- This feature only save basic information you need to configure/select rest of the options in the app (like messages, enable, etc.).

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
- Your Karoo needs an internet connection for messages to be sent correctly (mobile connected and linked with companion app if you've a karoo 3)
- Each provider has its own usage limitations, especially in free plans.
- Make sure you have sufficient balance if using paid plans.

## Privacy/Liability

- KActions does not store  personal information beyond what is necessary for its operation, obviously if you use a third part API (Whapi, CallmeBot, Textbelt or any information with webhooks) you're sharing this information with these services. KActions store information only in your Karoo device, but if you use a third party API, you need to understand you're sharing this info with third party service. 
- KActions doesn't have any relationship or partnership with Whapi, Callmebot or Textbelt, you need to read and accept their conditions. If you use KActions with any of these services, you accept this. 
- When you use Kactions you're agree you've all responsibility with the information (and use), for example don't spam, be careful it you link a webhook to make actions, etc. Kactions has no warranties, if you aren't agree with this you cannot use it.
- Please be careful with your personal information.

## Credits

- Developed by EnderThor.
- Uses Whapi, CallMeBot, TextBelt  for message sending.
- Uses the Karoo Extensions Framework developed by Hammerhead.

## Useful Links

- [Whapi Documentation](https://docs.whapi.io/)
- [CallMeBot API](https://www.callmebot.com/blog/free-api-whatsapp-messages/)
- [TextBelt SMS API](https://textbelt.com/)
- [Karoo Extensions Framework](https://github.com/hammerheadnav/karoo-ext)

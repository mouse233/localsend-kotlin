package io.github.mouse233.localsendkotlin.security

import java.math.BigInteger
import java.security.MessageDigest

/** LocalSend-compatible device-verification code. */
data class VerificationCode(val text: String, val iconNames: List<String>) {
    companion object {
        fun create(localFingerprint: String, remoteFingerprint: String): VerificationCode {
            val combined = listOf(localFingerprint, remoteFingerprint).sorted().joinToString("")
            // LocalSend renders only the first 128 bits of this digest as icons.
            val digest = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray(Charsets.UTF_8)).copyOfRange(0, 16)
            val iconFingerprint = digest.joinToString("") { "%02x".format(it) }
            return VerificationCode(combined, toIconNames(iconFingerprint))
        }

        private fun toIconNames(fingerprint: String): List<String> {
            val base = BigInteger.valueOf(ICON_ALPHABET.size.toLong())
            val length = kotlin.math.ceil(fingerprint.length * 4.0 * kotlin.math.ln(2.0) / kotlin.math.ln(ICON_ALPHABET.size.toDouble())).toInt()
            var value = BigInteger(fingerprint, 16)
            return MutableList(length) {
                val index = value.mod(base).toInt()
                value = value.divide(base)
                ICON_ALPHABET[index]
            }.asReversed()
        }

        // Order copied from LocalSend's fingerprint_alphabet.dart.
        private val ICON_ALPHABET = "ac_unit,accessibility,agriculture,alarm,album,anchor,android,apartment,apple,architecture,attach_file,attach_money,audiotrack,back_hand,backpack,badge,bakery_dining,balance,bathtub,battery_full,bento,biotech,blender,bluetooth,bolt,bookmark,brush,bug_report,build,cable,cake,calculate,calendar_today,campaign,casino,cast,castle,celebration,cell_tower,chair,change_history,chat,checkroom,church,circle,cloud,coffee_maker,colorize,computer,confirmation_number,content_cut,conveyor_belt,cookie,coronavirus,cottage,credit_card,cruelty_free,cyclone,delete,diamond,directions_bike,directions_boat,directions_bus,directions_car,door_front_door,earbuds,eco,edit,egg,elevator,email,emoji_events,emoji_nature,engineering,explore,extension,face,factory,fastfood,favorite,fence,festival,fingerprint,fire_extinguisher,fireplace,fitness_center,flag,flashlight_on,flight,flutter_dash,forklift,format_paint,gavel,grass,groups,handshake,hardware,headphones,healing,hearing,hexagon,history_edu,hive,home,hourglass_empty,houseboat,hub,ice_skating,icecream,inventory_2,iron,kebab_dining,key,keyboard,king_bed,kitchen,landscape,layers,light,lightbulb,link,liquor,local_bar,local_cafe,local_drink,local_florist,local_gas_station,local_laundry_service,local_mall,local_pizza,local_shipping,local_taxi,location_city,lock,luggage,map,markunread_mailbox,masks,medication,memory,menu,menu_book,mic,microwave,military_tech,monitor_heart,mood,mosque,motorcycle,mouse,movie,museum,newspaper,nightlight,notifications,oil_barrel,outdoor_grill,outlet,palette,park,pentagon,person,pets,phishing,phone,photo_camera,piano,pie_chart,place,pool,power,precision_manufacturing,print,propane_tank,psychology,public,push_pin,qr_code,radar,radio,ramen_dining,receipt,recycling,redeem,refresh,restaurant,rocket,room_service,route,router,sailing,satellite_alt,savings,school,science,search,settings,shelves,shield,shopping_basket,shopping_cart,shower,signpost,sim_card,smart_toy,smartphone,smoking_rooms,soap,solar_power,speaker,sports_esports,sports_football,sports_golf,sports_hockey,sports_motorsports,sports_soccer,sports_tennis,square,stadium,stairs,star,store,straighten,stroller,table_restaurant,tag,temple_buddhist,theater_comedy,theaters,thermostat,thumb_up,toggle_on,toll,tornado,toys,traffic,train,tune,umbrella,usb,vaccines,videocam,view_in_ar,visibility,voicemail,volcano,wallet,warehouse,warning,watch,water_drop,waves,wb_sunny,weekend,whatshot,wifi,wind_power,window,wine_bar,work".split(',')
    }
}

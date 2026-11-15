package org.liquidgram.ui

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

data class Country(val name: String, val iso: String, val code: String) {
    /** Flag emoji built from the ISO code's regional-indicator letters. */
    val flag: String get() = iso.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

// shortcut: the most common ~90 countries; add the rest of the ITU list if someone needs it.
val Countries = listOf(
    Country("Russia", "RU", "7"), Country("Kazakhstan", "KZ", "7"), Country("Belarus", "BY", "375"), Country("Ukraine", "UA", "380"),
    Country("United States", "US", "1"), Country("Canada", "CA", "1"), Country("United Kingdom", "GB", "44"), Country("Germany", "DE", "49"),
    Country("France", "FR", "33"), Country("Italy", "IT", "39"), Country("Spain", "ES", "34"), Country("Poland", "PL", "48"),
    Country("Turkey", "TR", "90"), Country("Uzbekistan", "UZ", "998"), Country("Kyrgyzstan", "KG", "996"), Country("Tajikistan", "TJ", "992"),
    Country("Turkmenistan", "TM", "993"), Country("Armenia", "AM", "374"), Country("Azerbaijan", "AZ", "994"), Country("Georgia", "GE", "995"),
    Country("Moldova", "MD", "373"), Country("Latvia", "LV", "371"), Country("Lithuania", "LT", "370"), Country("Estonia", "EE", "372"),
    Country("Finland", "FI", "358"), Country("Sweden", "SE", "46"), Country("Norway", "NO", "47"), Country("Denmark", "DK", "45"),
    Country("Iceland", "IS", "354"), Country("Netherlands", "NL", "31"), Country("Belgium", "BE", "32"), Country("Luxembourg", "LU", "352"),
    Country("Switzerland", "CH", "41"), Country("Austria", "AT", "43"), Country("Czechia", "CZ", "420"), Country("Slovakia", "SK", "421"),
    Country("Hungary", "HU", "36"), Country("Romania", "RO", "40"), Country("Bulgaria", "BG", "359"), Country("Serbia", "RS", "381"),
    Country("Croatia", "HR", "385"), Country("Slovenia", "SI", "386"), Country("Bosnia and Herzegovina", "BA", "387"), Country("Montenegro", "ME", "382"),
    Country("North Macedonia", "MK", "389"), Country("Albania", "AL", "355"), Country("Greece", "GR", "30"), Country("Cyprus", "CY", "357"),
    Country("Malta", "MT", "356"), Country("Portugal", "PT", "351"), Country("Ireland", "IE", "353"), Country("Israel", "IL", "972"),
    Country("United Arab Emirates", "AE", "971"), Country("Saudi Arabia", "SA", "966"), Country("Qatar", "QA", "974"), Country("Kuwait", "KW", "965"),
    Country("Jordan", "JO", "962"), Country("Lebanon", "LB", "961"), Country("Iraq", "IQ", "964"), Country("Iran", "IR", "98"),
    Country("Egypt", "EG", "20"), Country("Morocco", "MA", "212"), Country("Nigeria", "NG", "234"), Country("Kenya", "KE", "254"),
    Country("South Africa", "ZA", "27"), Country("India", "IN", "91"), Country("Pakistan", "PK", "92"), Country("Bangladesh", "BD", "880"),
    Country("Sri Lanka", "LK", "94"), Country("Nepal", "NP", "977"), Country("Mongolia", "MN", "976"), Country("China", "CN", "86"),
    Country("Hong Kong", "HK", "852"), Country("Taiwan", "TW", "886"), Country("Japan", "JP", "81"), Country("South Korea", "KR", "82"),
    Country("Vietnam", "VN", "84"), Country("Thailand", "TH", "66"), Country("Malaysia", "MY", "60"), Country("Singapore", "SG", "65"),
    Country("Indonesia", "ID", "62"), Country("Philippines", "PH", "63"), Country("Australia", "AU", "61"), Country("New Zealand", "NZ", "64"),
    Country("Brazil", "BR", "55"), Country("Argentina", "AR", "54"), Country("Mexico", "MX", "52"), Country("Colombia", "CO", "57"),
    Country("Chile", "CL", "56"), Country("Peru", "PE", "51"),
).sortedBy { it.name }

/** Country of the SIM card, else of the system locale, else the US. */
fun defaultCountry(context: Context): Country {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val iso = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
        .firstOrNull { it.length == 2 }?.uppercase()
    return Countries.firstOrNull { it.iso == iso } ?: Countries.first { it.iso == "US" }
}

/** Country whose dialling code matches the start of [digits] (longest code wins). */
fun countryForCode(code: String, current: Country?): Country? =
    if (current?.code == code) current else Countries.filter { it.code == code }.minByOrNull { if (it.iso == "RU" || it.iso == "US") 0 else 1 }

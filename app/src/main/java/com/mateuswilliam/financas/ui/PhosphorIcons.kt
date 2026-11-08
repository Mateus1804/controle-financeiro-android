package com.mateuswilliam.financas.ui

import androidx.annotation.DrawableRes
import com.mateuswilliam.financas.R

/** Uses the matching official Phosphor Fill asset for a selected Regular icon. */
@DrawableRes
fun selectedIcon(@DrawableRes icon: Int, selected: Boolean): Int {
    if (!selected) return icon
    return when (icon) {
        R.drawable.ic_overview -> R.drawable.ic_overview_fill
        R.drawable.ic_pending -> R.drawable.ic_pending_fill
        R.drawable.ic_settings -> R.drawable.ic_settings_fill
        R.drawable.ic_add -> R.drawable.ic_add_fill
        R.drawable.ic_income -> R.drawable.ic_income_fill
        R.drawable.ic_expense -> R.drawable.ic_expense_fill
        R.drawable.ic_transfer -> R.drawable.ic_transfer_fill
        R.drawable.ic_refund -> R.drawable.ic_refund_fill
        R.drawable.ic_chevron_left -> R.drawable.ic_chevron_left_fill
        R.drawable.ic_chevron_right -> R.drawable.ic_chevron_right_fill
        R.drawable.ic_chevron_down -> R.drawable.ic_chevron_down_fill
        R.drawable.ic_wallet -> R.drawable.ic_wallet_fill
        R.drawable.ic_calendar -> R.drawable.ic_calendar_fill
        R.drawable.ic_food -> R.drawable.ic_food_fill
        R.drawable.ic_home -> R.drawable.ic_home_fill
        R.drawable.ic_transport -> R.drawable.ic_transport_fill
        R.drawable.ic_health -> R.drawable.ic_health_fill
        R.drawable.ic_education -> R.drawable.ic_education_fill
        R.drawable.ic_leisure -> R.drawable.ic_leisure_fill
        R.drawable.ic_category -> R.drawable.ic_category_fill
        R.drawable.ic_bank -> R.drawable.ic_bank_fill
        R.drawable.ic_card -> R.drawable.ic_card_fill
        R.drawable.ic_lock -> R.drawable.ic_lock_fill
        R.drawable.ic_backup -> R.drawable.ic_backup_fill
        R.drawable.ic_restore -> R.drawable.ic_restore_fill
        R.drawable.ic_visibility -> R.drawable.ic_visibility_fill
        R.drawable.ic_visibility_off -> R.drawable.ic_visibility_off_fill
        R.drawable.ic_close -> R.drawable.ic_close_fill
        R.drawable.ic_search -> R.drawable.ic_search_fill
        R.drawable.ic_check -> R.drawable.ic_check_fill
        R.drawable.ic_notification -> R.drawable.ic_notification_fill
        R.drawable.ic_records -> R.drawable.ic_records_fill
        R.drawable.ic_more -> R.drawable.ic_more_fill
        R.drawable.ic_filter -> R.drawable.ic_filter_fill
        else -> icon
    }
}

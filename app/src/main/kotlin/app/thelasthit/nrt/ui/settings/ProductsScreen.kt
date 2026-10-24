package app.thelasthit.nrt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.LabelLimit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.product.word
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.ui.components.LastHitSheet
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.format.formDetail
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.packLimitText
import app.thelasthit.nrt.ui.format.perUnitLabel
import app.thelasthit.nrt.ui.format.productTitle
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.format.unitsName
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductsViewModel(private val graph: DataGraph) : ViewModel() {

    val products: StateFlow<List<UserProduct>?> =
        graph.products.everything.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setHidden(product: UserProduct, hidden: Boolean) {
        viewModelScope.launch { graph.products.setHidden(product.id, hidden) }
    }

    /** A product the catalogue doesn't have. It goes on the quick log too. */
    fun addByHand(name: String, form: ProductForm, perUnit: Dose, dailyLimit: Int?) {
        viewModelScope.launch { graph.products.addCustom(name, form, perUnit, label = LabelLimit(maxUnitsPerDay = dailyLimit)) }
    }

    /** A new name or daily limit. Any hourly limit from the pack stays. */
    fun update(product: UserProduct, name: String, dailyLimit: Int?) {
        viewModelScope.launch { graph.products.update(product.id, name, product.label.copy(maxUnitsPerDay = dailyLimit)) }
    }
}

@Composable
fun ProductsScreen(
    onBack: () -> Unit,
    onAddFromCatalogue: () -> Unit,
    viewModel: ProductsViewModel = graphViewModel { ProductsViewModel(it) },
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsPage(stringResource(R.string.products_title), stringResource(R.string.settings_back), onBack) {
        val all = products ?: return@SettingsPage
        Intro(stringResource(R.string.products_intro))
        Box(Modifier.padding(top = 20.dp)) {
            RowList {
                SettingsRow(
                    stringResource(R.string.products_add_catalogue),
                    stringResource(R.string.products_add_catalogue_detail),
                    first = true,
                    onClick = onAddFromCatalogue,
                    icon = R.drawable.ic_search,
                    chevron = true,
                )
                SettingsRow(
                    stringResource(R.string.products_add_hand),
                    stringResource(R.string.products_add_hand_detail),
                    first = false,
                    onClick = { sheet = NEW_PRODUCT },
                    icon = R.drawable.ic_plus,
                    chevron = true,
                )
            }
        }
        val inUse = all.filter { !it.isHidden }
        val hidden = all.filter { it.isHidden }
        SectionHeading(stringResource(R.string.products_in_use))
        if (inUse.isEmpty()) {
            FootNote(stringResource(R.string.products_none))
        } else {
            RowList {
                inUse.forEachIndexed { index, product ->
                    SettingsRow(
                        title = productTitle(product),
                        detail = packLimitText(product),
                        first = index == 0,
                        onClick = { sheet = product.id },
                        icon = product.form.icon,
                        trailing = {
                            TextLink(
                                stringResource(R.string.products_hide),
                                onClick = { viewModel.setHidden(product, true) },
                                description = stringResource(R.string.products_hide_description, productTitle(product)),
                            )
                        },
                    )
                }
            }
        }
        if (hidden.isNotEmpty()) {
            SectionHeading(stringResource(R.string.products_hidden))
            RowList {
                hidden.forEachIndexed { index, product ->
                    SettingsRow(
                        title = productTitle(product),
                        detail = stringResource(R.string.products_hidden_detail),
                        first = index == 0,
                        onClick = { sheet = product.id },
                        icon = product.form.icon,
                        muted = true,
                        trailing = {
                            TextLink(
                                stringResource(R.string.products_show),
                                onClick = { viewModel.setHidden(product, false) },
                                description = stringResource(R.string.products_show_description, productTitle(product)),
                            )
                        },
                    )
                }
            }
        }
        FootNote(stringResource(R.string.products_foot))

        sheet?.let { which ->
            val editing = all.firstOrNull { it.id == which }
            if (which == NEW_PRODUCT || editing != null) {
                ProductSheet(
                    editing = editing,
                    onDismiss = { sheet = null },
                    onAdd = { name, form, dose, limit -> viewModel.addByHand(name, form, dose, limit) },
                    onSave = { product, name, limit -> viewModel.update(product, name, limit) },
                )
            }
        }
    }
}

private const val NEW_PRODUCT = "new"

/** The forms a product can be added by hand as, in the order the sheet offers them. */
private val HandForms = listOf(
    ProductForm.GUM, ProductForm.LOZENGE, ProductForm.MINI_LOZENGE, ProductForm.PATCH_16H, ProductForm.PATCH_24H,
    ProductForm.MOUTH_SPRAY, ProductForm.NASAL_SPRAY, ProductForm.INHALATOR, ProductForm.MICROTAB, ProductForm.POUCH,
    ProductForm.OTHER,
)

@Composable
private fun formName(form: ProductForm): String = when (form) {
    ProductForm.PATCH_16H, ProductForm.PATCH_24H -> stringResource(formDetail(form))
    else -> form.word?.let { stringResource(it.text) } ?: stringResource(R.string.form_other)
}

/**
 * Adds a product by hand, or with [editing], changes a product's name and daily pack limit. The
 * strength of an existing product stays, so doses logged later match those before.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProductSheet(
    editing: UserProduct?,
    onDismiss: () -> Unit,
    onAdd: (String, ProductForm, Dose, Int?) -> Unit,
    onSave: (UserProduct, String, Int?) -> Unit,
) {
    val colors = LastHit.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var form by rememberSaveable { mutableStateOf(editing?.form ?: ProductForm.GUM) }
    var strength by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf(editing?.label?.maxUnitsPerDay?.toString().orEmpty()) }
    var tried by rememberSaveable { mutableStateOf(false) }

    val dose = editing?.perUnit ?: Dose.parseMg(strength)
    val limitValue = limit.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.takeIf { it in 1..999 }
    val limitOk = limit.isBlank() || limitValue != null
    val canSave = name.isNotBlank() && dose != null && limitOk
    val unit = editing?.unit ?: form.defaultUnit

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    LastHitSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (editing == null) R.string.hand_title else R.string.hand_edit_title),
                            style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp),
                            color = colors.text,
                        )
                        Text(
                            if (editing == null) stringResource(R.string.hand_detail) else stringResource(R.string.dose_named, formName(editing.form), editing.perUnit.mgText()),
                            style = LastHit.type.label,
                            color = colors.text2,
                        )
                    }
                    IconButton(onClick = ::close) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = colors.text)
                    }
                }
                SheetField(stringResource(R.string.hand_name)) {
                    SheetTextField(
                        value = name,
                        onChange = { name = it.take(40) },
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                if (editing == null) {
                    SheetField(stringResource(R.string.hand_type)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HandForms.forEach { each ->
                                Chip(formName(each), selected = each == form, kind = ChipKind.Choice, onClick = { form = each })
                            }
                        }
                    }
                    SheetField(stringResource(R.string.hand_strength)) {
                        SheetTextField(
                            value = strength,
                            onChange = { strength = it.take(8) },
                            suffix = stringResource(perUnitLabel(form.defaultUnit)),
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            error = if (tried && dose == null) stringResource(R.string.hand_bad_strength) else null,
                        )
                    }
                }
                SheetField(stringResource(R.string.hand_limit)) {
                    SheetTextField(
                        value = limit,
                        onChange = { limit = it.take(4) },
                        placeholder = stringResource(R.string.hand_limit_hint),
                        suffix = stringResource(R.string.hand_limit_unit, stringResource(unitsName(unit))),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                        error = if (!limitOk) stringResource(R.string.hand_bad_limit) else null,
                    )
                    Text(
                        stringResource(R.string.hand_limit_help),
                        style = LastHit.type.caption,
                        color = colors.text2,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)) {
                PrimaryButton(
                    onClick = {
                        tried = true
                        if (canSave) {
                            if (editing == null) onAdd(name, form, dose!!, limitValue) else onSave(editing, name, limitValue)
                            close()
                        }
                    },
                    enabled = name.isNotBlank(),
                ) { content ->
                    Text(
                        stringResource(if (editing == null) R.string.hand_add else R.string.hand_save),
                        style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                        color = content,
                    )
                    if (editing == null && dose != null) {
                        Text(
                            stringResource(R.string.dose_named, formName(form), dose.mgText()),
                            style = LastHit.type.label.copy(fontSize = 15.sp),
                            color = content.copy(alpha = 0.82f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SheetField(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        FieldLabel(title)
        content()
    }
}

/** A text field in the sheets' style, with an optional unit after the value and an error under it. */
@Composable
internal fun SheetTextField(
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null,
    suffix: String? = null,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LastHit.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = LastHit.type.body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        placeholder = placeholder?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it, style = LastHit.type.label.copy(fontSize = 15.sp), color = colors.text2) } },
        trailingIcon = trailing,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = colors.error) } },
        keyboardOptions = keyboard,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.text,
            unfocusedBorderColor = colors.lineStrong,
            errorBorderColor = colors.error,
            focusedPlaceholderColor = colors.text3,
            unfocusedPlaceholderColor = colors.text3,
            cursorColor = colors.text,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
        ),
    )
}

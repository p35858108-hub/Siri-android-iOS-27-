package com.assistant.siri.ui.capsule

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.assistant.siri.R
import com.assistant.siri.assistant.AssistantState
import com.assistant.siri.data.settings.AppSettings
import com.assistant.siri.ui.theme.CapsuleColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Ancho máximo de la cápsula expandida (en tablets y en horizontal no se estira del todo). */
private val MaxWideWidth = 560.dp

/** Radio máximo de las esquinas: pequeña es una píldora, grande es un contenedor redondeado. */
internal val CapsuleMaxCorner = 34.dp

/**
 * Opacidad del cristal oscuro de la cápsula expandida (1 = negro opaco). Más bajo deja ver más
 * el fondo desenfocado, pero resta contraste al texto. Sin desenfoque (antes de Android 12)
 * se usa una opacidad mayor para que el texto se lea bien.
 */
private const val GlassAlpha = 0.80f
private const val GlassAlphaWithoutBlur = 0.94f

internal enum class CapsuleKind(@StringRes val labelRes: Int) {
    IDLE(R.string.state_idle),
    LISTENING(R.string.state_listening),
    PROCESSING(R.string.state_thinking),
    TYPING(R.string.state_typing),
    RESPONDING(R.string.state_responding),
    ERROR(R.string.state_error)
}

internal fun AssistantState.kind(): CapsuleKind = when (this) {
    AssistantState.Idle -> CapsuleKind.IDLE
    is AssistantState.Listening -> CapsuleKind.LISTENING
    is AssistantState.Processing -> CapsuleKind.PROCESSING
    is AssistantState.Typing -> CapsuleKind.TYPING
    is AssistantState.Responding -> CapsuleKind.RESPONDING
    is AssistantState.Error -> CapsuleKind.ERROR
}

/**
 * Forma de la cápsula: píldora completa si es baja, esquinas redondeadas si es alta.
 * [cornerScale] va de 1 (esquinas normales) a 0 (esquinas rectas, pantalla completa).
 */
internal data class CapsuleShape(val cornerScale: Float = 1f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val maxCorner = with(density) { CapsuleMaxCorner.toPx() } * cornerScale
        val radius = min(size.height / 2f, maxCorner).coerceAtMost(size.width / 2f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius, radius)))
    }
}

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * La cápsula del asistente.
 *
 * Muestra por sí misma el reposo, la escucha y el procesamiento. Para escritura, respuesta
 * y error llama a [expandedContent], que recibe el estado actual y la altura máxima
 * disponible. Mientras se funde de un estado a otro puede recibir un estado distinto al
 * esperado, así que debe tolerar cualquiera.
 *
 * @param onClick se llama al tocar la cápsula en reposo, escuchando o procesando.
 * @param gestureModifier gestos sobre la cápsula (por ejemplo, deslizar para abrir la app).
 * @param morph de 0 a 1, se lee al dibujar: 0 es la cápsula normal y 1 ocupa toda la pantalla
 * con el color [morphColor] (así se convierte en la pantalla principal).
 */
@Composable
fun AssistantCapsule(
    state: AssistantState,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    anchor: CutoutAnchor = rememberCutoutAnchor(),
    onClick: (AssistantState) -> Unit = {},
    gestureModifier: Modifier = Modifier,
    morph: () -> Float = { 0f },
    morphColor: Color = CapsuleColors.Surface,
    expandedContent: @Composable (state: AssistantState, maxHeight: Dp) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    val kind = state.kind()
    val reduceMotion = settings.reduceMotion

    // 0 = cápsula en reposo sobre la cámara, 1 = expandida
    val progress = animateFloatAsState(
        targetValue = if (kind == CapsuleKind.IDLE) 0f else 1f,
        animationSpec = if (reduceMotion) {
            snap<Float>()
        } else {
            spring<Float>(dampingRatio = 0.86f, stiffness = 260f)
        },
        label = "capsuleExpansion"
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val rest = remember(anchor, containerWidth, containerHeight, density.density) {
            anchor.restShape(containerWidth, containerHeight, density.density)
        }
        val expandedTopPx = if (rest.edge == AnchorEdge.TOP) {
            rest.top
        } else {
            anchor.safeTop + with(density) { 8.dp.toPx() }
        }

        // ----- Tamaño de destino de cada estado (se adapta a la pantalla) -----
        val wide = minOf(maxWidth - 24.dp, MaxWideWidth)
        val hasPartial = (state as? AssistantState.Listening)?.partialText?.isNotBlank() == true
        val targetWidth: Dp = when (kind) {
            CapsuleKind.IDLE -> with(density) { rest.width.toDp() }
            CapsuleKind.LISTENING -> minOf(wide, if (hasPartial) 340.dp else 232.dp)
            CapsuleKind.PROCESSING -> minOf(wide, 176.dp)
            CapsuleKind.TYPING, CapsuleKind.RESPONDING -> wide
            CapsuleKind.ERROR -> minOf(wide, 360.dp)
        }
        val minHeightPx = with(density) {
            when (kind) {
                CapsuleKind.IDLE -> rest.height
                CapsuleKind.LISTENING -> 56.dp.toPx()
                CapsuleKind.PROCESSING -> 52.dp.toPx()
                CapsuleKind.TYPING -> 64.dp.toPx()
                CapsuleKind.RESPONDING -> 72.dp.toPx()
                CapsuleKind.ERROR -> 56.dp.toPx()
            }
        }
        val maxHeightPx = max(
            minHeightPx,
            min(containerHeight * 0.62f, containerHeight - expandedTopPx - with(density) { 24.dp.toPx() })
        )
        val maxHeightDp = with(density) { maxHeightPx.toDp() }

        // Altura natural del contenido de cada estado (medida al dibujar)
        val measuredHeights = remember { mutableStateMapOf<CapsuleKind, Int>() }
        val contentHeightPx = measuredHeights[kind] ?: 0
        val targetHeightPx = if (kind == CapsuleKind.IDLE) {
            rest.height
        } else {
            contentHeightPx.toFloat().coerceIn(minHeightPx, maxHeightPx)
        }
        val targetWidthPx = with(density) { targetWidth.toPx() }

        // Ancho y alto con muelles propios: se siente orgánico y se adapta al texto
        val sizeSpec: AnimationSpec<Float> = if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.82f, stiffness = 380f)
        }
        val widthPx = animateFloatAsState(
            targetValue = targetWidthPx,
            animationSpec = sizeSpec,
            label = "capsuleWidth"
        )
        val heightPx = animateFloatAsState(
            targetValue = targetHeightPx,
            animationSpec = sizeSpec,
            label = "capsuleHeight"
        )

        val description = stringResource(R.string.cd_capsule)
        val stateLabel = stringResource(kind.labelRes)
        val clickable = kind == CapsuleKind.IDLE ||
            kind == CapsuleKind.LISTENING ||
            kind == CapsuleKind.PROCESSING
        val interactionSource = remember { MutableInteractionSource() }
        val glow = settings.capsuleGlow

        val fadeInMillis = if (reduceMotion) 0 else 220
        val fadeInDelay = if (reduceMotion) 0 else 90
        val fadeOutMillis = if (reduceMotion) 0 else 90

        Box(
            Modifier
                .capsuleFrame(rest, expandedTopPx, widthPx, heightPx, progress, morph)
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val m = morph().coerceIn(0f, 1f)
                    shadowElevation = p * (1f - m) * 18.dp.toPx()
                    shape = CapsuleShape(1f - m)
                    clip = true
                    // Al final del gesto la superficie se desvanece y deja ver la pantalla de debajo
                    alpha = 1f - smoothStep(0.85f, 1f, m)
                    ambientShadowColor = Color.Black.copy(alpha = 0.45f)
                    spotShadowColor = Color.Black.copy(alpha = 0.55f)
                }
                .drawBehind {
                    drawCapsuleSurface(
                        progress = progress.value.coerceIn(0f, 1f),
                        glow = glow,
                        morph = morph().coerceIn(0f, 1f),
                        morphColor = morphColor
                    )
                }
                .then(gestureModifier)
        ) {
            // El contenido se maquetea siempre al ancho de destino y se centra dentro del marco
            // que se está animando, así que no "resbala" mientras la cápsula crece.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = 1f - smoothStep(0f, 0.45f, morph().coerceIn(0f, 1f)) }
                    .requiredWidth(targetWidth)
                    .then(
                        if (kind == CapsuleKind.IDLE) {
                            Modifier.height(with(density) { rest.height.toDp() })
                        } else {
                            Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)
                        }
                    )
                    .semantics {
                        contentDescription = description
                        stateDescription = stateLabel
                        liveRegion = LiveRegionMode.Polite
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = clickable,
                        role = Role.Button
                    ) { onClick(state) }
                    .onSizeChanged { size -> measuredHeights[kind] = size.height }
            ) {
                AnimatedContent(
                    targetState = kind,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween<Float>(fadeInMillis, delayMillis = fadeInDelay)) togetherWith
                            fadeOut(tween<Float>(fadeOutMillis)))
                            .using(SizeTransform(clip = false) { _, _ -> snap() })
                    },
                    contentAlignment = Alignment.TopCenter,
                    label = "capsuleContent"
                ) { contentKind ->
                    when (contentKind) {
                        CapsuleKind.IDLE -> Unit

                        CapsuleKind.LISTENING -> {
                            val listening = state as? AssistantState.Listening
                            ListeningContent(
                                level = listening?.level ?: 0f,
                                partialText = listening?.partialText.orEmpty(),
                                animate = !reduceMotion
                            )
                        }

                        CapsuleKind.PROCESSING -> ProcessingContent(animate = !reduceMotion)

                        else -> expandedContent(state, maxHeightDp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningContent(level: Float, partialText: String, animate: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ListeningIndicator(level = level, animate = animate)
        if (partialText.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = partialText,
                style = MaterialTheme.typography.bodyMedium,
                color = CapsuleColors.TextPrimary.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProcessingContent(animate: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        ThinkingIndicator(animate = animate)
    }
}

/**
 * Da el tamaño animado a la cápsula y la coloca. Parte del borde superior de la cámara y
 * se centra en la pantalla a medida que se expande. Con [morph] crece hasta ocupar todo
 * el espacio. Todo se lee en la fase de maquetación, así que animar no recompone.
 */
private fun Modifier.capsuleFrame(
    rest: RestShape,
    expandedTop: Float,
    widthPx: State<Float>,
    heightPx: State<Float>,
    progress: State<Float>,
    morph: () -> Float
): Modifier = layout { measurable, constraints ->
    val m = morph().coerceIn(0f, 1f)
    val width = lerp(widthPx.value, constraints.maxWidth.toFloat(), m)
        .roundToInt().coerceIn(0, constraints.maxWidth)
    val height = lerp(heightPx.value, constraints.maxHeight.toFloat(), m)
        .roundToInt().coerceIn(0, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))

    layout(constraints.maxWidth, constraints.maxHeight) {
        val p = progress.value.coerceIn(0f, 1f)
        val margin = lerp(0f, 12.dp.toPx(), p)
        val centerX = lerp(rest.centerX, constraints.maxWidth / 2f, p)
        val maxX = max(margin, constraints.maxWidth - placeable.width - margin)
        val x = (centerX - placeable.width / 2f).coerceIn(margin, maxX)

        val restTop = if (rest.edge == AnchorEdge.TOP) {
            rest.top
        } else {
            rest.centerY - placeable.height / 2f
        }
        val y = lerp(restTop, expandedTop, p)

        placeable.place(lerp(x, 0f, m).roundToInt(), lerp(y, 0f, m).roundToInt())
    }
}

/** Fondo negro, reflejo sutil arriba y borde fino. Se dibuja con el tamaño animado. */
private fun DrawScope.drawCapsuleSurface(
    progress: Float,
    glow: Boolean,
    morph: Float,
    morphColor: Color
) {
    val radius = min(size.height / 2f, CapsuleMaxCorner.toPx() * (1f - morph))
        .coerceAtMost(size.width / 2f)
    val corner = CornerRadius(radius, radius)
    val detail = 1f - morph

    // Negro puro en reposo (la cámara conserva su aspecto). Al expandirse pasa a cristal oscuro
    // translúcido, que deja ver el fondo desenfocado. Con el gesto de abrir la app se convierte
    // en el color del fondo de la app.
    val glass = CapsuleColors.Surface.copy(
        alpha = if (BlurSupport.isSupported) GlassAlpha else GlassAlphaWithoutBlur
    )
    val base = lerpColor(CapsuleColors.Black, glass, progress)
    drawRoundRect(color = lerpColor(base, morphColor, morph), cornerRadius = corner)

    // Reflejos de color muy tenues en el borde superior (azul, violeta y cálido): estáticos
    if (glow && progress > 0f && detail > 0f) {
        val glintRadius = min(size.width * 0.28f, size.height * 0.85f)
        val glints = listOf(
            0.26f to CapsuleColors.GlintBlue,
            0.50f to CapsuleColors.GlintViolet,
            0.74f to CapsuleColors.GlintWarm
        )
        for ((position, color) in glints) {
            val center = Offset(size.width * position, 0f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.34f), Color.Transparent),
                    center = center,
                    radius = glintRadius
                ),
                radius = glintRadius,
                center = center,
                alpha = progress * detail
            )
        }
    }

    // Borde sutil con algo más de luz arriba
    val stroke = 0.75.dp.toPx()
    val topAlpha = if (glow) 0.46f else 0.18f
    val bottomAlpha = if (glow) 0.12f else 0.06f
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = topAlpha), Color.White.copy(alpha = bottomAlpha))
        ),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius((radius - stroke / 2f).coerceAtLeast(0f)),
        style = Stroke(width = stroke),
        alpha = (0.25f + 0.75f * progress) * detail
    )
}

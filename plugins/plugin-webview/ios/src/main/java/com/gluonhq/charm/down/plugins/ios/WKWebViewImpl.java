package com.gluonhq.charm.down.plugins.ios;

import com.sun.javafx.css.converters.BooleanConverter;
import com.sun.javafx.css.converters.EnumConverter;
import com.sun.javafx.css.converters.SizeConverter;
import com.sun.javafx.geom.BaseBounds;
import com.sun.javafx.geom.PickRay;
import com.sun.javafx.geom.transform.Affine3D;
import com.sun.javafx.geom.transform.BaseTransform;
import com.sun.javafx.scene.DirtyBits;
import com.sun.javafx.scene.input.PickResultChooser;
import com.sun.javafx.tk.TKPulseListener;
import com.sun.javafx.tk.Toolkit;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.FontSmoothingType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WKWebViewImpl extends Parent {
    private String currentLocation, currentContent;
    static {
        System.loadLibrary("WKWebViewImpl");
    }

    public void loadURL(String url) {
        _loadUrl(handle, url);
    }

    public String getContent() {
        return currentContent;
    }

    static void checkThread() {
        Toolkit.getToolkit().checkFxUserThread();
    }

    private static final boolean DEFAULT_CONTEXT_MENU_ENABLED = true;
    private static final FontSmoothingType DEFAULT_FONT_SMOOTHING_TYPE = FontSmoothingType.LCD;
    private static final double DEFAULT_ZOOM = 1.0;
    private static final double DEFAULT_FONT_SCALE = 1.0;
    private static final double DEFAULT_MIN_WIDTH = 0;
    private static final double DEFAULT_MIN_HEIGHT = 0;
    private static final double DEFAULT_PREF_WIDTH = 800;
    private static final double DEFAULT_PREF_HEIGHT = 600;
    private static final double DEFAULT_MAX_WIDTH = Double.MAX_VALUE;
    private static final double DEFAULT_MAX_HEIGHT = Double.MAX_VALUE;

    // pointer to native WebViewImpl
    private final long handle;

    /**
     * The stage pulse listener registered with the toolkit.
     * This field guarantees that the listener will exist throughout
     * the whole lifetime of the WebView node. This field is necessary
     * because the toolkit references its stage pulse listeners weakly.
     */
    private final TKPulseListener stagePulseListener;

    private final ReadOnlyDoubleWrapper width = new ReadOnlyDoubleWrapper(this, "width");

    /**
     * Returns width of this {@code WebView}.
     */
    public final double getWidth() {
        return width.get();
    }

    /**
     * Width of this {@code WebView}.
     */
    public ReadOnlyDoubleProperty widthProperty() {
        return width.getReadOnlyProperty();
    }

    private final ReadOnlyDoubleWrapper height = new ReadOnlyDoubleWrapper(this, "height");

    /**
     * Returns height of this {@code WebView}.
     */
    public final double getHeight() {
        return height.get();
    }

    /**
     * Height of this {@code WebView}.
     */
    public ReadOnlyDoubleProperty heightProperty() {
        return height.getReadOnlyProperty();
    }

    /**
     * Zoom factor applied to the whole page contents.
     *
     * @defaultValue 1.0
     */
    private DoubleProperty zoom;

    /**
     * Sets current zoom factor applied to the whole page contents.
     * @param value zoom factor to be set
     * @see #zoomProperty()
     * @see #getZoom()
     * @since JavaFX 8.0
     */
    public final void setZoom(double value) {
        checkThread();
        zoomProperty().set(value);
    }

    /**
     * Returns current zoom factor applied to the whole page contents.
     * @return current zoom factor
     * @see #zoomProperty()
     * @see #setZoom(double value)
     * @since JavaFX 8.0
     */
    public final double getZoom() {
        return (this.zoom != null)
                ? this.zoom.get()
                : DEFAULT_ZOOM;
    }

    /**
     * Returns zoom property object.
     * @return zoom property object
     * @see #getZoom()
     * @see #setZoom(double value)
     * @since JavaFX 8.0
     */
    public final DoubleProperty zoomProperty() {
        if (zoom == null) {
            zoom = new StyleableDoubleProperty(DEFAULT_ZOOM) {
                @Override public void invalidated() {
                    Toolkit.getToolkit().checkFxUserThread();
                }

                @Override public CssMetaData<WKWebViewImpl, Number> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.ZOOM;
                }
                @Override public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override public String getName() {
                    return "zoom";
                }
            };
        }
        return zoom;
    }

    /**
     * Specifies scale factor applied to font. This setting affects
     * text content but not images and fixed size elements.
     *
     * @defaultValue 1.0
     */
    private DoubleProperty fontScale;

    public final void setFontScale(double value) {
        checkThread();
        fontScaleProperty().set(value);
    }

    public final double getFontScale() {
        return (this.fontScale != null)
                ? this.fontScale.get()
                : DEFAULT_FONT_SCALE;
    }

    public DoubleProperty fontScaleProperty() {
        if (fontScale == null) {
            fontScale = new StyleableDoubleProperty(DEFAULT_FONT_SCALE) {
                @Override public void invalidated() {
                    Toolkit.getToolkit().checkFxUserThread();
                }
                @Override public CssMetaData<WKWebViewImpl, Number> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.FONT_SCALE;
                }
                @Override public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override public String getName() {
                    return "fontScale";
                }
            };
        }
        return fontScale;
    }

    /**
     * Creates a {@code WebView} object.
     */
    public WKWebViewImpl() {
        long[] nativeHandle = new long[1];
        _initWebView(nativeHandle);
        getStyleClass().add("web-view");
        handle = nativeHandle[0];

        stagePulseListener = () -> {
            handleStagePulse();
        };
        focusedProperty().addListener((ov, t, t1) -> {
        });
        Toolkit.getToolkit().addStageTkPulseListener(stagePulseListener);

        final ChangeListener<Bounds> chListener = new ChangeListener<Bounds>() {

            @Override
            public void changed(ObservableValue<? extends Bounds> observable, Bounds oldValue, Bounds newValue) {
                WKWebViewImpl.this.impl_transformsChanged();
            }
        };

        parentProperty().addListener(new ChangeListener<Parent>(){

            @Override
            public void changed(ObservableValue<? extends Parent> observable, Parent oldValue, Parent newValue) {
                if (oldValue != null && newValue == null) {
                    // webview has been removed from scene
                    _removeWebView(handle);
                }

                if (oldValue != null) {
                    do {
                        oldValue.boundsInParentProperty().removeListener(chListener);
                        oldValue = oldValue.getParent();
                    } while (oldValue != null);
                }

                if (newValue != null) {
                    do {
                        final Node n = newValue;
                        newValue.boundsInParentProperty().addListener(chListener);
                        newValue = newValue.getParent();
                    } while (newValue != null);
                }
            }

        });

        layoutBoundsProperty().addListener(new ChangeListener<Bounds>() {

            @Override
            public void changed(ObservableValue<? extends Bounds> observable, Bounds oldValue, Bounds newValue) {
                Affine3D trans = calculateNodeToSceneTransform(WKWebViewImpl.this);
                _setTransform(handle,
                        trans.getMxx(), trans.getMxy(), trans.getMxz(), trans.getMxt(),
                        trans.getMyx(), trans.getMyy(), trans.getMyz(), trans.getMyt(),
                        trans.getMzx(), trans.getMzy(), trans.getMzz(), trans.getMzt());

            }

        });

        impl_treeVisibleProperty().addListener(new ChangeListener<Boolean>() {

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                _setVisible(handle, newValue);
            }
        });
    }
    // Resizing support. Allows arbitrary growing and shrinking.
    // Designed after javafx.scene.control.Control

    @Override public boolean isResizable() {
        return true;
    }

    @Override public void resize(double width, double height) {
        this.width.set(width);
        this.height.set(height);
        impl_markDirty(DirtyBits.NODE_GEOMETRY);
        impl_geomChanged();
        _setWidth(handle, width);
        _setHeight(handle, height);
    }

    /**
     * Called during layout to determine the minimum width for this node.
     *
     * @return the minimum width that this node should be resized to during layout
     */
    @Override public final double minWidth(double height) {
        return getMinWidth();
    }

    /**
     * Called during layout to determine the minimum height for this node.
     *
     * @return the minimum height that this node should be resized to during layout
     */
    @Override public final double minHeight(double width) {
        return getMinHeight();
    }


    /**
     * Called during layout to determine the preferred width for this node.
     *
     * @return the preferred width that this node should be resized to during layout
     */
    @Override public final double prefWidth(double height) {
        return getPrefWidth();
    }

    /**
     * Called during layout to determine the preferred height for this node.
     *
     * @return the preferred height that this node should be resized to during layout
     */
    @Override public final double prefHeight(double width) {
        return getPrefHeight();
    }
    /**
     * Called during layout to determine the maximum width for this node.
     *
     * @return the maximum width that this node should be resized to during layout
     */
    @Override public final double maxWidth(double height) {
        return getMaxWidth();
    }

    /**
     * Called during layout to determine the maximum height for this node.
     *
     * @return the maximum height that this node should be resized to during layout
     */
    @Override public final double maxHeight(double width) {
        return getMaxHeight();
    }

    /**
     * Minimum width property.
     */
    public DoubleProperty minWidthProperty() {
        if (minWidth == null) {
            minWidth = new StyleableDoubleProperty(DEFAULT_MIN_WIDTH) {
                @Override
                public void invalidated() {
                    if (getParent() != null) {
                        getParent().requestLayout();
                    }
                }
                @Override
                public CssMetaData<WKWebViewImpl, Number> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.MIN_WIDTH;
                }
                @Override
                public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override
                public String getName() {
                    return "minWidth";
                }
            };
        }
        return minWidth;
    }
    private DoubleProperty minWidth;

    /**
     * Sets minimum width.
     */
    public final void setMinWidth(double value) {
        minWidthProperty().set(value);
        _setWidth(handle, value);
    }

    /**
     * Returns minimum width.
     */
    public final double getMinWidth() {
        return (this.minWidth != null)
                ? this.minWidth.get()
                : DEFAULT_MIN_WIDTH;
    }

    /**
     * Minimum height property.
     */
    public DoubleProperty minHeightProperty() {
        if (minHeight == null) {
            minHeight = new StyleableDoubleProperty(DEFAULT_MIN_HEIGHT) {
                @Override
                public void invalidated() {
                    if (getParent() != null) {
                        getParent().requestLayout();
                    }
                }
                @Override
                public CssMetaData<WKWebViewImpl, Number> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.MIN_HEIGHT;
                }
                @Override
                public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override
                public String getName() {
                    return "minHeight";
                }
            };
        }
        return minHeight;
    }
    private DoubleProperty minHeight;

    /**
     * Sets minimum height.
     */
    public final void setMinHeight(double value) {
        minHeightProperty().set(value);
        _setHeight(handle, value);
    }

    /**
     * Sets minimum height.
     */
    public final double getMinHeight() {
        return (this.minHeight != null)
                ? this.minHeight.get()
                : DEFAULT_MIN_HEIGHT;
    }

    /**
     * Convenience method for setting minimum width and height.
     */
    public void setMinSize(double minWidth, double minHeight) {
        setMinWidth(minWidth);
        setMinHeight(minHeight);
        _setWidth(handle, minWidth);
        _setHeight(handle, minHeight);
    }

    /**
     * Preferred width property.
     */
    public DoubleProperty prefWidthProperty() {
        if (prefWidth == null) {
            prefWidth = new StyleableDoubleProperty(DEFAULT_PREF_WIDTH) {
                @Override
                public void invalidated() {
                    if (getParent() != null) {
                        getParent().requestLayout();
                    }
                }
                @Override
                public CssMetaData<WKWebViewImpl, Number> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.PREF_WIDTH;
                }
                @Override
                public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override
                public String getName() {
                    return "prefWidth";
                }
            };
        }
        return prefWidth;
    }
    private DoubleProperty prefWidth;

    /**
     * Sets preferred width.
     */
    public final void setPrefWidth(double value) {
        prefWidthProperty().set(value);
        _setWidth(handle, value);
    }

    /**
     * Returns preferred width.
     */
    public final double getPrefWidth() {
        return (this.prefWidth != null)
                ? this.prefWidth.get()
                : DEFAULT_PREF_WIDTH;
    }

    /**
     * Preferred height property.
     */
    public DoubleProperty prefHeightProperty() {
        if (prefHeight == null) {
            prefHeight = new StyleableDoubleProperty(DEFAULT_PREF_HEIGHT) {
                @Override
                public void invalidated() {
                    if (getParent() != null) {
                        getParent().requestLayout();
                    }
                }
                @Override
                public CssMetaData<WKWebViewImpl, Number> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.PREF_HEIGHT;
                }
                @Override
                public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override
                public String getName() {
                    return "prefHeight";
                }
            };
        }
        return prefHeight;
    }
    private DoubleProperty prefHeight;

    /**
     * Sets preferred height.
     */
    public final void setPrefHeight(double value) {
        prefHeightProperty().set(value);
        _setHeight(handle, value);
    }

    /**
     * Returns preferred height.
     */
    public final double getPrefHeight() {
        return (this.prefHeight != null)
                ? this.prefHeight.get()
                : DEFAULT_PREF_HEIGHT;
    }

    /**
     * Convenience method for setting preferred width and height.
     */
    public void setPrefSize(double prefWidth, double prefHeight) {
        setPrefWidth(prefWidth);
        setPrefHeight(prefHeight);
        _setWidth(handle, prefWidth);
        _setHeight(handle, prefHeight);
    }

    /**
     * Maximum width property.
     */
    public DoubleProperty maxWidthProperty() {
        if (maxWidth == null) {
            maxWidth = new StyleableDoubleProperty(DEFAULT_MAX_WIDTH) {
                @Override
                public void invalidated() {
                    if (getParent() != null) {
                        getParent().requestLayout();
                    }
                }
                @Override
                public CssMetaData<WKWebViewImpl, Number> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.MAX_WIDTH;
                }
                @Override
                public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override
                public String getName() {
                    return "maxWidth";
                }
            };
        }
        return maxWidth;
    }
    private DoubleProperty maxWidth;

    /**
     * Sets maximum width.
     */
    public final void setMaxWidth(double value) {
        maxWidthProperty().set(value);
        _setWidth(handle, value);
    }

    /**
     * Returns maximum width.
     */
    public final double getMaxWidth() {
        return (this.maxWidth != null)
                ? this.maxWidth.get()
                : DEFAULT_MAX_WIDTH;
    }

    /**
     * Maximum height property.
     */
    public DoubleProperty maxHeightProperty() {
        if (maxHeight == null) {
            maxHeight = new StyleableDoubleProperty(DEFAULT_MAX_HEIGHT) {
                @Override
                public void invalidated() {
                    if (getParent() != null) {
                        getParent().requestLayout();
                    }
                }
                @Override
                public CssMetaData<WKWebViewImpl, Number> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.MAX_HEIGHT;
                }
                @Override
                public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override
                public String getName() {
                    return "maxHeight";
                }
            };
        }
        return maxHeight;
    }
    private DoubleProperty maxHeight;

    /**
     * Sets maximum height.
     */
    public final void setMaxHeight(double value) {
        maxHeightProperty().set(value);
        _setHeight(handle, value);
    }

    /**
     * Returns maximum height.
     */
    public final double getMaxHeight() {
        return (this.maxHeight != null)
                ? this.maxHeight.get()
                : DEFAULT_MAX_HEIGHT;
    }

    /**
     * Convenience method for setting maximum width and height.
     */
    public void setMaxSize(double maxWidth, double maxHeight) {
        setMaxWidth(maxWidth);
        setMaxHeight(maxHeight);
        _setWidth(handle, maxWidth);
        _setHeight(handle, maxHeight);
    }


    /**
     * Specifies a requested font smoothing type : gray or LCD.
     *
     * The width of the bounding box is defined by the widest row.
     *
     * Note: LCD mode doesn't apply in numerous cases, such as various
     * compositing modes, where effects are applied and very large glyphs.
     *
     * @defaultValue FontSmoothingType.LCD
     * @since JavaFX 2.2
     */
    private ObjectProperty<FontSmoothingType> fontSmoothingType;

    public final void setFontSmoothingType(FontSmoothingType value) {
        fontSmoothingTypeProperty().set(value);
    }

    public final FontSmoothingType getFontSmoothingType() {
        return (this.fontSmoothingType != null)
                ? this.fontSmoothingType.get()
                : DEFAULT_FONT_SMOOTHING_TYPE;
    }

    public final ObjectProperty<FontSmoothingType> fontSmoothingTypeProperty() {
        if (this.fontSmoothingType == null) {
            this.fontSmoothingType = new StyleableObjectProperty<FontSmoothingType>(DEFAULT_FONT_SMOOTHING_TYPE) {
                @Override
                public void invalidated() {
                    Toolkit.getToolkit().checkFxUserThread();
                }
                @Override
                public CssMetaData<WKWebViewImpl, FontSmoothingType> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.FONT_SMOOTHING_TYPE;
                }
                @Override
                public Object getBean() {
                    return WKWebViewImpl.this;
                }
                @Override
                public String getName() {
                    return "fontSmoothingType";
                }
            };
        }
        return this.fontSmoothingType;
    }

    /**
     * Specifies whether context menu is enabled.
     *
     * @defaultValue true
     * @since JavaFX 2.2
     */
    private BooleanProperty contextMenuEnabled;

    public final void setContextMenuEnabled(boolean value) {
        contextMenuEnabledProperty().set(value);
    }

    public final boolean isContextMenuEnabled() {
        return contextMenuEnabled == null
                ? DEFAULT_CONTEXT_MENU_ENABLED
                : contextMenuEnabled.get();
    }

    public final BooleanProperty contextMenuEnabledProperty() {
        if (contextMenuEnabled == null) {
            contextMenuEnabled = new StyleableBooleanProperty(DEFAULT_CONTEXT_MENU_ENABLED) {
                @Override public void invalidated() {
                    Toolkit.getToolkit().checkFxUserThread();
                }

                @Override public CssMetaData<WKWebViewImpl, Boolean> getCssMetaData() {
                    return WKWebViewImpl.StyleableProperties.CONTEXT_MENU_ENABLED;
                }

                @Override public Object getBean() {
                    return WKWebViewImpl.this;
                }

                @Override public String getName() {
                    return "contextMenuEnabled";
                }
            };
        }
        return contextMenuEnabled;
    }

    /**
     * Super-lazy instantiation pattern from Bill Pugh.
     */
    private static final class StyleableProperties {

        private static final CssMetaData<WKWebViewImpl, Boolean> CONTEXT_MENU_ENABLED
                = new CssMetaData<WKWebViewImpl, Boolean>(
                "-fx-context-menu-enabled",
                BooleanConverter.getInstance(),
                DEFAULT_CONTEXT_MENU_ENABLED)
        {
            @Override public boolean isSettable(WKWebViewImpl view) {
                return view.contextMenuEnabled == null || !view.contextMenuEnabled.isBound();
            }
            @Override public StyleableProperty<Boolean> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Boolean>)view.contextMenuEnabledProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, FontSmoothingType> FONT_SMOOTHING_TYPE
                = new CssMetaData<WKWebViewImpl, FontSmoothingType>(
                "-fx-font-smoothing-type",
                new EnumConverter<FontSmoothingType>(FontSmoothingType.class),
                DEFAULT_FONT_SMOOTHING_TYPE) {
            @Override
            public boolean isSettable(WKWebViewImpl view) {
                return view.fontSmoothingType == null || !view.fontSmoothingType.isBound();
            }
            @Override
            public StyleableProperty<FontSmoothingType> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<FontSmoothingType>)view.fontSmoothingTypeProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, Number> ZOOM
                = new CssMetaData<WKWebViewImpl, Number>(
                "-fx-zoom",
                SizeConverter.getInstance(),
                DEFAULT_ZOOM) {
            @Override public boolean isSettable(WKWebViewImpl view) {
                return view.zoom == null || !view.zoom.isBound();
            }
            @Override public StyleableProperty<Number> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Number>)view.zoomProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, Number> FONT_SCALE
                = new CssMetaData<WKWebViewImpl, Number>(
                "-fx-font-scale",
                SizeConverter.getInstance(),
                DEFAULT_FONT_SCALE) {
            @Override
            public boolean isSettable(WKWebViewImpl view) {
                return view.fontScale == null || !view.fontScale.isBound();
            }
            @Override
            public StyleableProperty<Number> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Number>)view.fontScaleProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, Number> MIN_WIDTH
                = new CssMetaData<WKWebViewImpl, Number>(
                "-fx-min-width",
                SizeConverter.getInstance(),
                DEFAULT_MIN_WIDTH) {
            @Override
            public boolean isSettable(WKWebViewImpl view) {
                return view.minWidth == null || !view.minWidth.isBound();
            }
            @Override
            public StyleableProperty<Number> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Number>)view.minWidthProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, Number> MIN_HEIGHT
                = new CssMetaData<WKWebViewImpl, Number>(
                "-fx-min-height",
                SizeConverter.getInstance(),
                DEFAULT_MIN_HEIGHT) {
            @Override
            public boolean isSettable(WKWebViewImpl view) {
                return view.minHeight == null || !view.minHeight.isBound();
            }
            @Override
            public StyleableProperty<Number> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Number>)view.minHeightProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, Number> MAX_WIDTH
                = new CssMetaData<WKWebViewImpl, Number>(
                "-fx-max-width",
                SizeConverter.getInstance(),
                DEFAULT_MAX_WIDTH) {
            @Override
            public boolean isSettable(WKWebViewImpl view) {
                return view.maxWidth == null || !view.maxWidth.isBound();
            }
            @Override
            public StyleableProperty<Number> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Number>)view.maxWidthProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, Number> MAX_HEIGHT
                = new CssMetaData<WKWebViewImpl, Number>(
                "-fx-max-height",
                SizeConverter.getInstance(),
                DEFAULT_MAX_HEIGHT) {
            @Override
            public boolean isSettable(WKWebViewImpl view) {
                return view.maxHeight == null || !view.maxHeight.isBound();
            }
            @Override
            public StyleableProperty<Number> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Number>)view.maxHeightProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, Number> PREF_WIDTH
                = new CssMetaData<WKWebViewImpl, Number>(
                "-fx-pref-width",
                SizeConverter.getInstance(),
                DEFAULT_PREF_WIDTH) {
            @Override
            public boolean isSettable(WKWebViewImpl view) {
                return view.prefWidth == null || !view.prefWidth.isBound();
            }
            @Override
            public StyleableProperty<Number> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Number>)view.prefWidthProperty();
            }
        };

        private static final CssMetaData<WKWebViewImpl, Number> PREF_HEIGHT
                = new CssMetaData<WKWebViewImpl, Number>(
                "-fx-pref-height",
                SizeConverter.getInstance(),
                DEFAULT_PREF_HEIGHT) {
            @Override
            public boolean isSettable(WKWebViewImpl view) {
                return view.prefHeight == null || !view.prefHeight.isBound();
            }
            @Override
            public StyleableProperty<Number> getStyleableProperty(WKWebViewImpl view) {
                return (StyleableProperty<Number>)view.prefHeightProperty();
            }
        };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables
                    = new ArrayList<CssMetaData<? extends Styleable, ?>>(Parent.getClassCssMetaData());
            styleables.add(CONTEXT_MENU_ENABLED);
            styleables.add(FONT_SMOOTHING_TYPE);
            styleables.add(ZOOM);
            styleables.add(FONT_SCALE);
            styleables.add(MIN_WIDTH);
            styleables.add(PREF_WIDTH);
            styleables.add(MAX_WIDTH);
            styleables.add(MIN_HEIGHT);
            styleables.add(PREF_HEIGHT);
            styleables.add(MAX_HEIGHT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * @return The CssMetaData associated with this class, which may include the
     * CssMetaData of its super classes.
     * @since JavaFX 8.0
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return WKWebViewImpl.StyleableProperties.STYLEABLES;
    }

    /**
     * {@inheritDoc}
     * @since JavaFX 8.0
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    // event handling

    private void handleStagePulse() {
        // The stage pulse occurs before the scene pulse.
        // Here the page content is updated before CSS/Layout/Sync pass
        // is initiated by the scene pulse. The update may
        // change the WebView children and, if so, the children should be
        // processed right away during the scene pulse.

        // The WebView node does not render its pending render queues
        // while it is invisible. Therefore, we should not schedule new
        // render queues while the WebView is invisible to prevent
        // the list of render queues from growing infinitely.
        // Also, if and when the WebView becomes invisible, the currently
        // pending render queues, if any, become obsolete and should be
        // discarded.

        boolean reallyVisible = impl_isTreeVisible()
                && getScene() != null
                && getScene().getWindow() != null
                && getScene().getWindow().isShowing();

        if (reallyVisible) {
            if (impl_isDirty(DirtyBits.WEBVIEW_VIEW)) {
                Scene.impl_setAllowPGAccess(true);
                //getPGWebView().update(); // creates new render queues
                Scene.impl_setAllowPGAccess(false);
            }
        } else {
            _setVisible(handle, false);
        }
    }

    /**
     * @treatAsPrivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    @Override protected void impl_pickNodeLocal(PickRay pickRay, PickResultChooser result) {
        impl_intersects(pickRay, result);
    }

    @Override protected ObservableList<Node> getChildren() {
        return super.getChildren();
    }

    // Node stuff

    /**
     * @treatAsPrivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    @Override public BaseBounds impl_computeGeomBounds(BaseBounds bounds, BaseTransform tx) {
        bounds.deriveWithNewBounds(0, 0, 0, (float) getWidth(), (float)getHeight(), 0);
        tx.transform(bounds, bounds);
        return bounds;
    }

    /**
     * @treatAsPrivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    @Override protected boolean impl_computeContains(double localX, double localY) {
        // Note: Local bounds contain test is already done by the caller. (Node.contains()).
        return true;
    }

    /**
     * @treatAsPrivate implementation detail
     * @deprecated This is an internal API that is not intended for use and will be removed in the next version
     */
    @Deprecated
    @Override public void impl_updatePeer() {
        super.impl_updatePeer();
        //PGWebView peer = getPGWebView();

        if (impl_isDirty(DirtyBits.NODE_GEOMETRY)) {
            //peer.resize((float)getWidth(), (float)getHeight());
        }
        if (impl_isDirty(DirtyBits.WEBVIEW_VIEW)) {
            //peer.requestRender();
        }
    }

    private static Affine3D calculateNodeToSceneTransform(Node node) {
        final Affine3D transform = new Affine3D();
        do {
            transform.preConcatenate(node.impl_getLeafTransform());
            node = node.getParent();
        } while (node != null);

        return transform;
    }

    @Deprecated
    @Override public void impl_transformsChanged() {
        super.impl_transformsChanged();

        Affine3D trans = calculateNodeToSceneTransform(this);
        _setTransform(handle,
                trans.getMxx(), trans.getMxy(), trans.getMxz(), trans.getMxt(),
                trans.getMyx(), trans.getMyy(), trans.getMyz(), trans.getMyt(),
                trans.getMzx(), trans.getMzy(), trans.getMzz(), trans.getMzt());
    }

    long getNativeHandle() {
        return handle;
    }


    // native callbacks
    private void notifyLoadStarted() {
        currentLocation = null;
        currentContent = null;
    }
    private void notifyLoadFinished(String loc, String content) {
        currentLocation = loc;
        currentContent = content;
    }
    private void notifyLoadFailed() {
        currentLocation = null;
        currentContent = null;
    }
    private void notifyJavaCall(String arg) {

    }


    /* Inits native WebView and returns its pointer in the given array */
    private native void _initWebView(long[] nativeHandle);

    /* Sets width of the native WebView  */
    private native void _setWidth(long handle, double w);

    /* Sets height of the native WebView  */
    private native void _setHeight(long handle, double h);

    /* Sets visibility of the native WebView  */
    private native void _setVisible(long handle, boolean v);

    /* Removes the native WebView from scene */
    private native void _removeWebView(long handle);

    /* Applies transform on the native WebView  */
    private native void _setTransform(long handle,
                                      double mxx, double mxy, double mxz, double mxt,
                                      double myx, double myy, double myz, double myt,
                                      double mzx, double mzy, double mzz, double mzt);

    /* Loads a web page */
    private native void _loadUrl(long handle, String url);

    /* Loads the given content directly */
    private native void _loadContent(long handle, String content);

    /* Executes a script in the context of the current page */
    private native String _executeScript(long handle, String script);
}

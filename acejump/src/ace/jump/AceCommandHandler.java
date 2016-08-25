package ace.jump;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import acejump.Activator;

public class AceCommandHandler extends AbstractHandler {
	private boolean drawNow = false;
	private char jumpTargetChar;
	private Map<Character, Integer> offsetForCharacter = new HashMap<Character, Integer>();

    public static final char INFINITE_JUMP_CHAR = '/';
    private static final String MARKER_CHARSET = "asdfjeghiybcmnopqrtuvwkl";
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final ITextEditor te;
		final ISourceViewer sv;
		final StyledText st;
		
		if (    null == (te = getActiveTextEditor())
		     || null == (te.getSelectionProvider().getSelection())
		     || null == (sv = getSourceViewer(te))
	         || null == (st = getStyledTextFromTextEditor(te))) {
			return null;
		}
		
        offsetForCharacter.clear();
		add_paint_listener__to__draw_jump_target_markers(st, sv);

		final Shell shell = new Shell(Display.getDefault(), SWT.MODELESS);
        shell.setBounds(1, 1, 1, 1);

		new org.eclipse.swt.widgets.Canvas(shell, SWT.ALPHA).addKeyListener(new KeyListener() {
		    @Override public void keyReleased(KeyEvent e) {}
		    @Override public void keyPressed(KeyEvent e) {
		    	if (Character.isISOControl(e.character)) {
		    		return;
		    	}
		    	
		        if (drawNow) {
		        	if (e.character >= 'a' && e.character <= 'z') {
						selectOrJumpNow(e.character, st, te);	
	                    drawNow = false;
						shell.close();
		        	}
				} else {
                    drawNow = e.keyCode != 27;
                    jumpTargetChar = e.character;
                    if (e.keyCode == 27) {
                        shell.close();
                    }
                }
                st.redraw();
            }
		});

		shell.open();
		return null;
	}

	protected void selectOrJumpNow(char ch, StyledText st, ITextEditor te) {
		Integer off = offsetForCharacter.get(ch);
		if (off != null) {
			st.setSelection(off);
		}
	}
	
	private void add_paint_listener__to__draw_jump_target_markers(final StyledText st, final ISourceViewer sv) {
		if (alreadyHasPaintListenerFor(st))
			return;
		
		PaintListener pl = new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				if (drawNow) {
                    draw_jump_target_markers(st, sv, e.gc);
                }
			}

            List<Integer> get_offsets_of_char(final StyledText st, final ISourceViewer sv) {
                List<Integer> offsets = new ArrayList<Integer>();

                int start = ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(sv.getTopIndexStartOffset());
				int end = ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(sv.getBottomIndexEndOffset());
				String src = st.getText(start, end);
                int caretOffset =  ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(st.getCaretOffset());

                for (int i = 0; i < src.length(); ++i) {
                    if (isMatch(src, i, jumpTargetChar)) {
                        int off = start + i;

                        if ( /*char_is_at_caret_and_should_ignore =*/ caretOffset == off) {
                            continue;
                        }

                        offsets.add(off);
                    }
                }

                return offsets;
            }

			private boolean isMatch(String src, int i, char match) {
				char c = src.charAt(i);
				if (Character.toLowerCase(c) != Character.toLowerCase(match)) {
					return false;
				}

				if (i > 0) {
					char prev = src.charAt(i - 1);
					if (!Character.isLetter(prev))
						return true;

					return Character.isUpperCase(c) && Character.isLowerCase(prev);
				}

				return false;
			}

			private void draw_jump_target_markers(final StyledText st, final ISourceViewer sv, GC gc) {
				List<Integer> offsets = get_offsets_of_char(st, sv);

				char marker = 'a';
                for (int i = 0; i < offsets.size() && marker <= 'z'; ++i) {
					offsetForCharacter.put(marker, offsets.get(i));
					++marker;
				}

				for (Map.Entry<Character, Integer> kv : offsetForCharacter.entrySet()) {
					drawMarkerAt(kv.getValue(), gc, st, kv.getKey());
				}
			}
			
			private void drawMarkerAt(int offset, GC gc, StyledText st, char marker) {
				String word = Character.toString(marker);

				Rectangle bounds = st.getTextBounds(offset, offset);
				gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
				Point textExtent = gc.textExtent(word);

				int ex = 0;
				int dex = 2 * ex;
				gc.fillRoundRectangle(bounds.x - ex, bounds.y - ex, textExtent.x + dex, textExtent.y + dex, 0, 0);

				gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_RED));
				gc.drawString(word, bounds.x, bounds.y, true);
			}
		};
		st.addPaintListener(pl);
	}

    ////////////////////////////////////////////////////////////////////////////
	private List<StyledText> listeners = new ArrayList<StyledText>();
	
	private boolean alreadyHasPaintListenerFor(final StyledText st) {
		if (listeners.contains(st))
			return true;
		
		listeners.add(st);
		st.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				listeners.remove(st);
			}
		});
		
		return false;
	}
	
    ////////////////////////////////////////////////////////////////////////////
	private ITextEditor getActiveTextEditor() {
		IEditorPart ae = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
        return ae instanceof ITextEditor ?  (ITextEditor) ae : null;
	}
	
	private ISourceViewer getSourceViewer(ITextEditor te) {
		try {
			Method m = AbstractTextEditor.class.getDeclaredMethod("getSourceViewer");
			m.setAccessible(true);
			return (ISourceViewer) m.invoke(te);
		} catch (Exception e1) {
			Activator.log(e1);
			return null;
		}
	}
	
	private StyledText getStyledTextFromTextEditor(ITextEditor te) {
		Control c = te.getAdapter(Control.class);
		return c instanceof StyledText ? (StyledText) c : null;
	}
	
    ////////////////////////////////////////////////////////////////////////////
}

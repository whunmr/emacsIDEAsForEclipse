package ace.jump;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
			char letterCounter;
			@Override
			public void paintControl(PaintEvent e) {
				if (drawNow) {
                    draw_jump_target_markers(st, sv, e.gc);
                }
			}

			private void draw_jump_target_markers(final StyledText st, final ISourceViewer sv, GC gc) {
                int start = ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(sv.getTopIndexStartOffset());
                int end = sv.getBottomIndexEndOffset();
				end = ((ITextViewerExtension5) sv).modelOffset2WidgetOffset(end);

				String src = st.getText(start, end).toLowerCase(Locale.ENGLISH);
				int i = 0;
				int len = st.getCharCount();
				letterCounter = 'a';
				char current = Character.toLowerCase(jumpTargetChar);
				while (true) {
					if (isMatch(src, i, current)) {
						int off = start + i;
						if (off >= len) {
							break;
						}
						drawNextCharAt(off, gc, st);
						if (letterCounter - 1 == 'z') {
							break;
						}
					}
					i++;
					if (i >= src.length())
						break;
				}
			}

			private boolean isMatch(String src, int i, char match) {
				char c = src.charAt(i);
				if (c == match) {
					if (i == 0)
						return true;
					if (Character.isLetter(c) == false)
						return true;
					char prev = src.charAt(i - 1);
					if (Character.isLetter(prev))
						return false;
					return true;
				}
				return false;
			}


			private void drawNextCharAt(int offset, GC gc, StyledText st) {
				String word = Character.toString(letterCounter);
				offsetForCharacter.put(letterCounter, offset);
				letterCounter++;

				Rectangle bounds = st.getTextBounds(offset, offset);
				gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
				Point textExtent = gc.textExtent(word);
				int ex = 0;
				int dex = 2 * ex;
				gc.fillRoundRectangle(bounds.x - ex, bounds.y - ex, textExtent.x + dex, textExtent.y + dex, 4, 4);
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

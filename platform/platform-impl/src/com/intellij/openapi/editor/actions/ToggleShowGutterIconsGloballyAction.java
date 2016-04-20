/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAware;

public class ToggleShowGutterIconsGloballyAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return EditorSettingsExternalizable.getInstance().areGutterIconsShown();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    EditorSettingsExternalizable.getInstance().setGutterIconsShown(state);
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor != null && editor.getSettings().areGutterIconsShown() != state) {
      editor.getSettings().setGutterIconsShown(state);
    }
    EditorFactory.getInstance().refreshAllEditors();
  }
}

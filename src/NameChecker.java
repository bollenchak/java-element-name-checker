import java.util.EnumSet;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementScanner6;
import javax.tools.Diagnostic.Kind;

public class NameChecker {

	private Messager messager;

	public NameChecker(ProcessingEnvironment processingEnvironment) {
		this.messager = processingEnvironment.getMessager();
	}

	public void checkNames(Element element) {
		new NameCheckScanner().scan(element);
	}

	private class NameCheckScanner extends ElementScanner6<Void, Void> {

		/**
		 * 检查类
		 */
		@Override
		public Void visitType(TypeElement e, Void p) {
			scan(e.getTypeParameters(), p);
			checkCamelCase(e, true);
			super.visitType(e, p);
			return null;
		}

		/**
		 * 检查方法
		 */
		@Override
		public Void visitExecutable(ExecutableElement e, Void p) {
			if (e.getKind() == ElementKind.METHOD) {
				Name name = e.getSimpleName();
				if (name.contentEquals(e.getEnclosingElement().getSimpleName())) {
					messager.printMessage(Kind.WARNING, "一个普通方法" + name + "不应该与类名重复以免构造函数产生混淆", e);
				}
				checkCamelCase(e, false);
				super.visitExecutable(e, p);
			}
			return null;
		}

		/**
		 * 检查变量
		 */
		@Override
		public Void visitVariable(VariableElement e, Void p) {
			if (e.getKind() == ElementKind.ENUM_CONSTANT || e.getConstantValue() != null || heuristicallyConstant(e)) {
				checkAllCaps(e);
			} else {
				checkCamelCase(e, false);
			}
			return null;
		}

		/**
		 * 判断变量是否为常量
		 */
		private boolean heuristicallyConstant(VariableElement e) {
			return e.getEnclosingElement().getKind() == ElementKind.INTERFACE || e.getKind() == ElementKind.FIELD && e.getModifiers()
					.containsAll(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL));
		}

		/**
		 * 检查驼峰命名
		 */
		private void checkCamelCase(Element e, boolean initialCaps) {
			String name = e.getSimpleName().toString();
			messager.printMessage(Kind.NOTE, "开始检查方法名:" + name);
			boolean previousUpper = false;
			boolean conventional = true;
			int firstCodePoint = name.codePointAt(0);
			if (Character.isUpperCase(firstCodePoint)) {
				previousUpper = true;
				if (!initialCaps) {
					messager.printMessage(Kind.WARNING, "名称" + name + "应当以小写字母开头", e);
					return;
				}
			} else if (Character.isLowerCase(firstCodePoint)) {
				if (initialCaps) {
					messager.printMessage(Kind.WARNING, "名称" + name + "应当以大写字母开头", e);
				}
			} else {
				conventional = false;
			}

			if (conventional) {
				int cp = firstCodePoint;
				for (int i = Character.charCount(cp); i < name.length(); i += Character.charCount(cp)) {
					cp = name.codePointAt(i);
					if (Character.isUpperCase(cp)) {
						if (previousUpper) {
							conventional = false;
							break;
						}
						previousUpper = true;
					} else {
						previousUpper = false;
					}
				}
			}
			if (!conventional) {
				messager.printMessage(Kind.WARNING, "名称" + name + "应当符合驼峰命名法", e);
			}
		}

		/**
		 * 检查常量名
		 */
		private void checkAllCaps(Element element) {
			String name = element.getSimpleName().toString();
			boolean conventional = true;
			int firstCodePoint = name.codePointAt(0);
			if (!Character.isUpperCase(firstCodePoint)) {
				conventional = false;
			} else {
				boolean previousUnderscore = false;
				int cp = firstCodePoint;
				for (int i = Character.charCount(cp); i < name.length(); i += Character.charCount(cp)) {
					cp = name.codePointAt(i);
					if (cp == (int) '_') {
						if (previousUnderscore) {
							conventional = false;
							break;
						}
						previousUnderscore = true;
					} else {
						previousUnderscore = false;
						if (!Character.isUpperCase(cp) && !Character.isDigit(cp)) {
							conventional = false;
							break;
						}
					}
				}
			}
			if (!conventional) {
				messager.printMessage(Kind.WARNING, "常量" + name + "应当全部以大写字母或下划线命名，并以字母开头", element);
			}
		}
	}
}

package me.coley.recaf.assemble.ast.insn;

import me.coley.recaf.assemble.ast.ArgType;
import me.coley.recaf.assemble.ast.HandleInfo;
import me.coley.recaf.assemble.ast.Printable;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Invoke dynamic instruction.
 *
 * @author Matt Coley
 */
public class IndyInstruction extends AbstractInstruction {
	private final String name;
	private final String desc;
	private final HandleInfo bsmHandle;
	private final List<BsmArg> bsmArguments;

	/**
	 * @param opcode
	 * 		Invoke dynamic opcode.
	 * @param name
	 * 		Name of target method.
	 * @param desc
	 * 		Descriptor of target method.
	 * @param bsmHandle
	 * 		Bootstrap method handle.
	 * @param bsmArguments
	 * 		Bootstrap method arguments.
	 */
	public IndyInstruction(String opcode, String name, String desc, HandleInfo bsmHandle, List<BsmArg> bsmArguments) {
		super(opcode);
		this.name = name;
		this.desc = desc;
		this.bsmHandle = bsmHandle;
		this.bsmArguments = bsmArguments;
	}

	/**
	 * @return Name of target method.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return Descriptor of target method.
	 */
	public String getDesc() {
		return desc;
	}

	/**
	 * @return Bootstrap method handle.
	 */
	public HandleInfo getBsmHandle() {
		return bsmHandle;
	}

	/**
	 * @return Bootstrap method arguments.
	 */
	public List<BsmArg> getBsmArguments() {
		return bsmArguments;
	}

	@Override
	public InstructionType getInsnType() {
		return InstructionType.INDY;
	}

	@Override
	public String print() {
		String handle = bsmHandle.print();
		String args = bsmArguments.stream()
				.map(BsmArg::print)
				.collect(Collectors.joining(", "));
		return String.format("%s %s %s handle(%s) args(%s)",
				getOpcode(), getName(), getDesc(), handle, args);
	}

	/**
	 * Helper for determining arg value types.
	 */
	public static class BsmArg implements Printable {
		private final ArgType type;
		private final Object value;

		/**
		 * @param type
		 * 		Type of value.
		 * @param value
		 * 		Value instance.
		 */
		public BsmArg(ArgType type, Object value) {
			this.type = type;
			this.value = value;
		}

		/**
		 * @return Type of value.
		 */
		public ArgType getType() {
			return type;
		}

		/**
		 * @return Value instance.
		 */
		public Object getValue() {
			return value;
		}

		@Override
		public String print() {
			switch (type) {
				case TYPE:
					Type type = (Type) value;
					if (type.getSort() == Type.OBJECT)
						return type.getInternalName();
					else
						return type.getDescriptor();
				case STRING:
					return "\"" + value + "\"";
				case HANDLE:
					HandleInfo info = (HandleInfo) value;
					return info.print();
				case INTEGER:
				case FLOAT:
				case DOUBLE:
				case LONG:
				default:
					return String.valueOf(value);
			}
		}

		@Override
		public String toString() {
			return "ARG[" + type + ":" + value + ']';
		}
	}
}

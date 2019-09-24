package me.coley.recaf.mapping;

import me.coley.recaf.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Proguard mappings file implmentation. <br>
 * Unlike the other mapping formats the intent of this mapper is to <i>undo</i> Proguard mappings,
 * not applying them.
 *
 * @author Matt
 */
public class ProguardMappings extends Mappings {
	private static final String FAIL = "Invalid Proguard mappings, ";
	private static final String NAME_LINE = "^.+:";
	private static final String SPLITTER = "( |->)+";
	private Map<String, String> obfToClean = new HashMap<>();
	private Map<String, String> cleanToObf = new HashMap<>();

	/**
	 * Constructs mappings from a given file.
	 *
	 * @param file
	 * 		A file containing proguard styled mappings.
	 *
	 * @throws IOException
	 * 		Thrown if the file could not be read.
	 */
	public ProguardMappings(File file) throws IOException {
		super(file);
	}

	@Override
	protected Map<String, String> parse(String text) {
		obfToClean = new HashMap<>();
		cleanToObf = new HashMap<>();
		String[] lines = StringUtil.splitNewline(text);
		collectNames(lines);
		parseMembers(lines);
		return obfToClean;
	}

	private void collectNames(String[] lines) {
		int line = 0;
		for(String lineStr : lines) {
			line++;
			// Skip comments line
			if(lineStr.startsWith("#"))
				continue;
			// Only look at name lines
			if(lineStr.matches(NAME_LINE)) {
				try {
					String[] split = lineStr.split("( |->)+");
					String clean = internalize(split[0]);
					String obf = internalize(split[1]);
					obf = obf.substring(0, obf.indexOf(':'));
					obfToClean.put(obf, clean);
					cleanToObf.put(clean, obf);
				} catch(IndexOutOfBoundsException ex) {
					throw new IllegalArgumentException(FAIL + "failed parsing line " + line, ex);
				}
			}
		}
	}

	private void parseMembers(String[] lines) {
		int line = 0;
		String currentObf = null;
		for(String lineStr : lines) {
			line++;
			// Skip comments line
			if(lineStr.startsWith("#"))
				continue;
			// Mark current class
			if(lineStr.matches(NAME_LINE)) {
				currentObf = internalize(lineStr.substring(lineStr.lastIndexOf(' ') + 1, lineStr.indexOf(':')));
				continue;
			}
			if(currentObf == null)
				throw new IllegalArgumentException(FAIL + "failed parsing line " + line + ", no class context");
			// Handle members
			if(!lineStr.contains("(")) {
				// Field
				// <type> <clean-name> -> <obf-name>
				String[] split = lineStr.trim().split(SPLITTER);
				String clean = split[1];
				String obf = split[2];
				/*
				String type = split[0];
				if (!isPrimitive(type))
					type = "L" + internalize(type) + ";";
				else
					type = internalize(type);
				*/
				obfToClean.put(currentObf + "." + obf, clean);
			} else {
				// Skip constructors
				if (lineStr.contains("init>"))
					continue;
				// Method 64:168:void updateStream() -> i
				// <start>:<finish>:<ret-type> <name><qualified-desc> -> <obf-name>
				// <ret-type> <name::qualified-desc> -> <obf-name>
				String[] split = null;
				if (lineStr.contains(":"))
					split = lineStr.substring(lineStr.lastIndexOf(":") + 1).trim().split(SPLITTER);
				else
					split = lineStr.trim().split(SPLITTER);
				// Return type
				String cleanRet = internalize(split[0]);
				String obfRet = cleanToObf.getOrDefault(cleanRet, cleanRet);
				// Parse the desc
				// name(name,name)
				String cleanDefintion = split[1];
				String clean = cleanDefintion.substring(0, cleanDefintion.indexOf('('));
				String[] progaurdArgs = cleanDefintion
						.substring(cleanDefintion.indexOf('(') + 1, cleanDefintion.length() - 1)
						.split(",");
				if (progaurdArgs.length == 1 && progaurdArgs[0].isEmpty())
					progaurdArgs = new String[0];
				for (int i = 0; i < progaurdArgs.length; i++) {
					String type = progaurdArgs[i];
					if (isPrimitive(type))
						progaurdArgs[i] = internalize(progaurdArgs[i]);
					else
						progaurdArgs[i] = "L" + internalize(progaurdArgs[i]) + ";";
				}
				String desc = "(" + String.join("", progaurdArgs) + ")" + obfRet;
				String obf = split[2];
				obfToClean.put(currentObf + "." + obf + desc, clean);
			}
		}
	}

	private String internalize(String name) {
		switch(name) {
			case "int":
				return "I";
			case "float":
				return "F";
			case "double":
				return "D";
			case "long":
				return "J";
			case "boolean":
				return "Z";
			case "short":
				return "S";
			case "byte":
				return "B";
			case "void":
				return "V";
			default:
				return name.replace('.', '/');
		}
	}

	private boolean isPrimitive(String name) {
		switch(name) {
			case "int":
			case "float":
			case "double":
			case "long":
			case "boolean":
			case "short":
			case "byte":
			case "void":
				return true;
			default:
				return false;
		}
	}
}
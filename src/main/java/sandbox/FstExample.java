package sandbox;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.io.IOException;
import java.net.URL;

import de.uni_mannheim.desq.dictionary.Dictionary;
import de.uni_mannheim.desq.dictionary.DictionaryIO;
import de.uni_mannheim.desq.fst.Fst;
import de.uni_mannheim.desq.io.DelSequenceReader;
import de.uni_mannheim.desq.io.SequenceReader;
import de.uni_mannheim.desq.patex.PatEx;

public class FstExample {

	void icdm16() throws IOException {
		
		
		URL dictFile = getClass().getResource("/icdm16-example/dict.del");
		URL dataFile = getClass().getResource("/icdm16-example/data.del");

		// load the dictionary
		Dictionary dict = DictionaryIO.loadFromDel(dictFile.openStream(), false);

		// update hierarchy
		SequenceReader dataReader = new DelSequenceReader(dataFile.openStream(), false);
		dict.incCounts(dataReader);
		dict.recomputeFids();
		
		String patternExpression = "[c|d]([A^|B=^]+)e";
		//String patternExpression = "[A|B]c";
		
		// create fst
		patternExpression = ".* [" + patternExpression.trim() + "]";
		PatEx patEx = new PatEx(patternExpression, dict);
		Fst fst = patEx.translate();
		fst.print("./fst");
		
		fst.minimize();
		fst.print("./fst-min");
		
		
	}
	
	public static void main(String[] args) throws IOException {
		new FstExample().icdm16();
	}
}

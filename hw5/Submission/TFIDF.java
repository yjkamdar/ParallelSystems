
//Single Author info:
//yjkamdar Yash J Kamdar

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.apache.spark.api.java.function.PairFunction;

import scala.Tuple2;

/*
 * Main class of the TFIDF Spark implementation.
 * Author: Tyler Stocksdale
 * Date:   10/31/2017
 */
public class TFIDF {

    static boolean DEBUG = false;

    public static void main(String[] args) throws Exception {
	// Check for correct usage
	if (args.length != 1) {
	    System.err.println("Usage: TFIDF <input dir>");
	    System.exit(1);
	}

	// Create a Java Spark Context
	SparkConf conf = new SparkConf().setAppName("TFIDF");
	JavaSparkContext sc = new JavaSparkContext(conf);

	// Load our input data
	// Output is: ( filePath , fileContents ) for each file in inputPath
	String inputPath = args[0];
	JavaPairRDD<String, String> filesRDD = sc.wholeTextFiles(inputPath);

	// Get/set the number of documents (to be used in the IDF job)
	long numDocs = filesRDD.count();

	// Print filesRDD contents
	if (DEBUG) {
	    List<Tuple2<String, String>> list = filesRDD.collect();
	    System.out.println("------Contents of filesRDD------");
	    for (Tuple2<String, String> tuple : list) {
		System.out.println("(" + tuple._1 + ") , (" + tuple._2.trim() + ")");
	    }
	    System.out.println("--------------------------------");
	}

	/*
	 * Initial Job Creates initial JavaPairRDD from filesRDD Contains each
	 * word@document from the corpus and also attaches the document size for later
	 * use
	 * 
	 * Input: ( filePath , fileContents ) Map: ( (word@document) , docSize )
	 */
	JavaPairRDD<String, Integer> wordsRDD = filesRDD
		.flatMapToPair(new PairFlatMapFunction<Tuple2<String, String>, String, Integer>() {
		    public Iterable<Tuple2<String, Integer>> call(Tuple2<String, String> x) {
			// Collect data attributes
			String[] filePath = x._1.split("/");
			String document = filePath[filePath.length - 1];
			String fileContents = x._2;
			String[] words = fileContents.split("\\s+");
			int docSize = words.length;

			// Output to Arraylist
			ArrayList ret = new ArrayList();
			for (String word : words) {
			    ret.add(new Tuple2(word.trim() + "@" + document, docSize));
			}
			return ret;
		    }
		});

	// Print wordsRDD contents
	if (DEBUG) {
	    List<Tuple2<String, Integer>> list = wordsRDD.collect();
	    System.out.println("------Contents of wordsRDD------");
	    for (Tuple2<String, Integer> tuple : list) {
		System.out.println("(" + tuple._1 + ") , (" + tuple._2 + ")");
	    }
	    System.out.println("--------------------------------");
	}

	/*
	 * TF Job (Word Count Job + Document Size Job) Gathers all data needed for TF
	 * calculation from wordsRDD
	 *
	 * Input: ( (word@document) , docSize ) Map: ( (word@document) , (1/docSize) )
	 * Reduce: ( (word@document) , (wordCount/docSize) )
	 */
	JavaPairRDD<String, String> tfRDD = wordsRDD.mapToPair(tup -> {
	    return new Tuple2<String, String>(tup._1, "1/" + String.valueOf(tup._2));
	}).reduceByKey((a, b) -> {
	    String[] aSplit = a.split("/");
	    String[] bSplit = b.split("/");
	    int wc = Integer.valueOf(aSplit[0]) + Integer.valueOf(bSplit[0]);
	    return String.valueOf(wc) + "/" + aSplit[1];
	});

	// Print tfRDD contents
	if (DEBUG) {
	    List<Tuple2<String, String>> list = tfRDD.collect();
	    System.out.println("-------Contents of tfRDD--------");
	    for (Tuple2<String, String> tuple : list) {
		System.out.println("(" + tuple._1 + ") , (" + tuple._2 + ")");
	    }
	    System.out.println("--------------------------------");
	}

	/*
	 * IDF Job Gathers all data needed for IDF calculation from tfRDD
	 *
	 * Input: ( (word@document) , (wordCount/docSize) ) Map: ( word , (1/document) )
	 * Reduce: ( word , (numDocsWithWord/document1,document2...) ) Map: (
	 * (word@document) , (numDocs/numDocsWithWord) )
	 */
	JavaPairRDD<String, String> idfRDD = tfRDD.mapToPair(tup -> {
	    String[] tupSplit = tup._1.split("@");
	    return new Tuple2<String, String>(tupSplit[0], "1/" + tupSplit[1]);
	}).reduceByKey((a, b) -> {
	    String[] aSplit = a.split("/");
	    String[] bSplit = b.split("/");
	    int sum = Integer.valueOf(aSplit[0]) + Integer.valueOf(bSplit[0]);
	    String docList=aSplit[1]+","+bSplit[1];
	    return sum + "/" + docList;
	}).flatMapToPair(tup -> {
	    List<Tuple2<String,String>> list=new ArrayList<>();
	    String[] valSplit = tup._2.split("/");
	    for(String doc:valSplit[1].split(",")) {
		list.add(new Tuple2<String, String>(tup._1 + "@" + doc, numDocs + "/" + valSplit[0]));
	    }
	    return list;
	});

	// Print idfRDD contents
	if (DEBUG) {
	    List<Tuple2<String, String>> list = idfRDD.collect();
	    System.out.println("-------Contents of idfRDD-------");
	    for (Tuple2<String, String> tuple : list) {
		System.out.println("(" + tuple._1 + ") , (" + tuple._2 + ")");
	    }
	    System.out.println("--------------------------------");
	}

	/*
	 * TF * IDF Job Calculates final TFIDF value from tfRDD and idfRDD
	 *
	 * Input: ( (word@document) , (wordCount/docSize) ) [from tfRDD] Map: (
	 * (word@document) , TF )
	 * 
	 * Input: ( (word@document) , (numDocs/numDocsWithWord) ) [from idfRDD] Map: (
	 * (word@document) , IDF )
	 * 
	 * Union: ( (word@document) , TF ) U ( (word@document) , IDF ) Reduce: (
	 * (word@document) , TFIDF ) Map: ( (document@word) , TFIDF )
	 *
	 * where TF = wordCount/docSize where IDF = ln(numDocs/numDocsWithWord) where
	 * TFIDF = TF * IDF
	 */
	JavaPairRDD<String, Double> tfFinalRDD = tfRDD
		.mapToPair(new PairFunction<Tuple2<String, String>, String, Double>() {
		    public Tuple2<String, Double> call(Tuple2<String, String> x) {
			double wordCount = Double.parseDouble(x._2.split("/")[0]);
			double docSize = Double.parseDouble(x._2.split("/")[1]);
			double TF = wordCount / docSize;
			return new Tuple2(x._1, TF);
		    }
		});

	JavaPairRDD<String, Double> idfFinalRDD = idfRDD.mapToPair(tup -> {
	    String[] split = tup._2.split("/");
	    return new Tuple2<String, Double>(tup._1,
		    Math.log((double) Integer.valueOf(split[0]) / (double) Integer.valueOf(split[1])));
	});

	JavaPairRDD<String, Double> tfidfRDD = tfFinalRDD.union(idfFinalRDD).reduceByKey((a, b) -> a * b)
		.mapToPair(tup -> {
		    String[] split = tup._1.split("@");
		    return new Tuple2<String, Double>(split[1] + "@" + split[0], tup._2);
		});

	// Print tfidfRDD contents in sorted order
	Map<String, Double> sortedMap = new TreeMap<>();
	List<Tuple2<String, Double>> list = tfidfRDD.collect();
	for (Tuple2<String, Double> tuple : list) {
	    sortedMap.put(tuple._1, tuple._2);
	}
	if (DEBUG)
	    System.out.println("-------Contents of tfidfRDD-------");
	for (String key : sortedMap.keySet()) {
	    System.out.println(key + "\t" + sortedMap.get(key));
	}
	if (DEBUG)
	    System.out.println("--------------------------------");
    }
}
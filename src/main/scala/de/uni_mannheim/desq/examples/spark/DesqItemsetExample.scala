package de.uni_mannheim.desq.examples.spark

import de.uni_mannheim.desq.Desq._
import de.uni_mannheim.desq.mining.spark.{DesqCount, DesqDataset, DesqMiner}
import de.uni_mannheim.desq.dictionary.{DefaultBuilderFactory, Dictionary, ItemsetBuilderFactory}
import de.uni_mannheim.desq.patex.PatExToItemsetPatEx
import it.unimi.dsi.fastutil.ints.{AbstractIntCollection, IntArrayList, IntList}
import org.apache.spark.{SparkConf, SparkContext}

import scala.io.Source

/**
  * Created by sulbrich on 18.09.2017
  */
object DesqItemsetExample {

  /** run on small icdm16 dataset **/
  def icdm16(compare: Boolean = false)(implicit sc: SparkContext) {

    //val patternExpression = "[c|d] (A)+ B" //"[c|d|e] (A)!*&(B)+&a1!+ [d|e]" "[c|d] (A)+ B" "(.)? . c"
    val patternExpression = "(-B=)"
    val sigma = 1
    val conf = DesqCount.createConf(patternExpression, sigma)
    if(compare){
      println("\n ==== sequence query =====")
      ExampleUtils.runIcdm16(conf)
    }
    println("\n ==== itemset query =====")
    ExampleUtils.runIcdm16(conf, asItemset = true)
  }

  /** run/eval itemset query on fimi-retail data and stores metrics in CSVs in data-local/ **/
  def fimi_retail()(implicit sc: SparkContext) {

    val patEx = "(39)&(41)&.!* (.)!{2,5}&.!* "//"(39)&(41)!{0,7}&.!*"
    val minSupport = 500

    val data = "data-local/fimi_retail/retail.dat"
    val dict = Option.apply(Dictionary.loadFrom("data-local/fimi_retail/dict.json", sc))

    println("\n ==== FIMI - Retail: Itemset query =====")
    runItemsetMiner(
      rawData = data,
      patEx = patEx,
      minSupport = minSupport,
      extDict = dict)
  }

  /** run itemset query on nyt91 data**/
  def nyt91()(implicit sc: SparkContext) {

    val patEx = "(.)!{3}&.!*" // PATTY: n-grams of (max?) length 3 (Up to 4 per sentence???)
    //val patEx = "(..)"
    val minSupport = 100000
    val data = DesqDataset.load("data-local/nyt-1991-data")

    runItemsetMiner(
      rawData = data,
      patEx = patEx,
      minSupport = minSupport
    )
  }

  def sequenceOfItemsets(dataPath: String = "data/itemset-example/data.dat",
                         dictPath: String = "data/itemset-example/dict.json",
                         logPrefix: String = "data-local/ItemsetEx_",
                         patEx: String = "[c|d] / (B)",
                         minSupport: Int = 1)(implicit sc: SparkContext): Unit = {
    //val patEx = "[c|d] / (B)"
    //val minSupport = 1

    val dict = Dictionary.loadFrom(dictPath)

    println("\n === ItemsetExample - Itemset Query ===")
    runItemsetMiner(
      rawData = dataPath,
      patEx = patEx,
      minSupport = minSupport,
      extDict = Option.apply(dict)
    )

    println("\n === ItemsetExample - Sequence of Itemsets Query ===")
    runItemsetMiner(
      rawData = dataPath,
      patEx = patEx,
      minSupport = minSupport,
      extDict = Option.apply(dict),
      itemsetSeparator = Option.apply("/")
    )
  }

  /**
    * Generic method to run Queries on ItemSet Data
    */
  def runItemsetMiner[T](rawData: T,
                          patEx:String,
                          minSupport: Int = 1000,
                          extDict: Option[Dictionary] = None,
                          itemsetSeparator: Option[String] = None
                        )(implicit sc: SparkContext): (DesqMiner, DesqDataset) ={

    // Manage different data inputs (DesqDataset, FilePath, RDD)
    var data: DesqDataset = null
    val factory =
      if(extDict.isDefined && itemsetSeparator.isDefined)
        new ItemsetBuilderFactory(extDict.get, itemsetSeparator.get)
      else if (extDict.isDefined)
        new ItemsetBuilderFactory(extDict.get)
      else if (itemsetSeparator.isDefined)
        new ItemsetBuilderFactory(itemsetSeparator.get)
      else new ItemsetBuilderFactory()
    rawData match {
      case dds: DesqDataset => //Build from existing DesqDataset
        data = DesqDataset.buildFromStrings(dds.toSids, Option.apply(factory))
      case file: String => //Build from space delimited file
        data = DesqDataset.buildFromStrings(sc.textFile(file).map(s => s.split(" ")), Option.apply(factory))
      case _ =>
        println("ERROR: Unsupported input type")
        return (null, null)
    }

    //Convert PatEx
    val itemsetPatEx =
      if (itemsetSeparator.isDefined)
        //sequence of itemsets
        new PatExToItemsetPatEx(patEx, itemsetSeparator.get).translate()
      else
        //itemset
        new PatExToItemsetPatEx(patEx).translate()

    //Print some information
    println("\nConverted PatEx: " + patEx +"  ->  " + itemsetPatEx)
    println("\nDictionary size: " + data.dict.size())
    //data.dict.writeJson(System.out)
    //println("\nSeparatorGid: " + data.itemsetSeparatorGid + "(" + data.getCfreqOfSeparator + ")")
    println("\nFirst 10 (of " + data.sequences.count() + ") Input Sequences:")
    data.print(10)

    // Init Desq Miner
    val confDesq = DesqCount.createConf(itemsetPatEx, minSupport)
    confDesq.setProperty("desq.mining.prune.irrelevant.inputs", true)
    confDesq.setProperty("desq.mining.use.two.pass", false)
    confDesq.setProperty("desq.mining.optimize.permutations", false)

    //Run Miner
    val (miner, result) = ExampleUtils.runVerbose(data,confDesq)

    (miner, result)
  }


  def convertDesqDatasetToItemset(targetPath: String,
                                  sourceDataPath: String,
                                  sourceDictPath: String = "",
                                  itemsetSeparator: String = "/"
                                  )(implicit sc: SparkContext): DesqDataset = {

    //Define factory for building the itemset DesqDataset
    val factory =
      if(sourceDictPath != ""){
        new ItemsetBuilderFactory(Dictionary.loadFrom(sourceDictPath),itemsetSeparator)
      }else{
        new ItemsetBuilderFactory(itemsetSeparator)
      }

    //Convert
    println("Start conversion ... ")
    val convertedData = ExampleUtils.buildDesqDatasetFromDelFile(sc,sourceDataPath,factory)

    //Store
    println("Saving converted data to " + targetPath)
    convertedData.copyWithRecomputedCountsAndFids().save(targetPath)

    //Return
    convertedData
  }

  def checkItemsetDesqDataset(path: String)(implicit sc: SparkContext){
    val dds = DesqDataset.load(path)
    dds.toFids().sequences.foreach(row => {
      var lastFid = -1
      for(fid: Int <- row.toIntArray()) {
        if (lastFid < fid) {
          //correct descending order
          lastFid = fid
        } else {
          throw new Error("Incorrect ordering in itemset DesqDataset")
        }
      }
    })
  }


  def main(args: Array[String]) {
    //Init SparkConf
    val conf = new SparkConf().setAppName(getClass.getName).setMaster("local")
    initDesq(conf)
    implicit val sc: SparkContext = new SparkContext(conf)
    conf.set("spark.kryoserializer.buffer.max", "2047m")

    //icdm16()
    //icdm16(compare = true)
    //evalIdcm16

    //fimi_retail()
    //fimi_retail()

    //nyt91()

    //sequenceOfItemsets(patEx = "[c|d] / (-/)") //on example

    /*sequenceOfItemsets(
      eval = true,
      dataPath = "data-local/fimi_retail/retail_sequences.dat",
      dictPath = "data-local/fimi_retail/dict.json",
      logPrefix = "data-local/Fimi_Seq_",
      patEx = "(-/) /{1,2} (-/)",
      minSupport = 100

    )

    convertDesqDatasetToItemset(
      "data-local/nyt_itemset/",
      "data-local/nyt/nyt-data-gid.del",
      "data-local/nyt/nyt-dict.avro.gz"
    )*/

/*
    convertDesqDatasetToItemset(
      "data-local/amazon_itemset/",
      "data-local/amazon/amazon-data-gid.del",
      "data-local/amazon/amazon-dict.avro.gz"
    )*/

    checkItemsetDesqDataset("data-local/amazon_itemset/")

  }

}

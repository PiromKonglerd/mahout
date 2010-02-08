/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.text;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Group;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.OptionException;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.commandline.Parser;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.mahout.common.HadoopUtil;
import org.apache.mahout.utils.vectors.common.PartialVectorMerger;
import org.apache.mahout.utils.vectors.lucene.LuceneIterable;
import org.apache.mahout.utils.vectors.text.DictionaryVectorizer;
import org.apache.mahout.utils.vectors.text.DocumentProcessor;
import org.apache.mahout.utils.vectors.tfidf.TFIDFConverter;

/**
 * Converts a given set of sequence files into SparseVectors
 * 
 */
public final class SparseVectorsFromSequenceFiles {
  
  private SparseVectorsFromSequenceFiles() {}
  
  public static void main(String[] args) throws Exception {
    DefaultOptionBuilder obuilder = new DefaultOptionBuilder();
    ArgumentBuilder abuilder = new ArgumentBuilder();
    GroupBuilder gbuilder = new GroupBuilder();
    
    Option inputDirOpt = obuilder.withLongName("input").withRequired(true)
        .withArgument(
          abuilder.withName("input").withMinimum(1).withMaximum(1).create())
        .withDescription(
          "input dir containing the documents in sequence file format")
        .withShortName("i").create();
    
    Option outputDirOpt = obuilder
        .withLongName("outputDir")
        .withRequired(true)
        .withArgument(
          abuilder.withName("outputDir").withMinimum(1).withMaximum(1).create())
        .withDescription("The output directory").withShortName("o").create();
    Option minSupportOpt = obuilder.withLongName("minSupport").withArgument(
      abuilder.withName("minSupport").withMinimum(1).withMaximum(1).create())
        .withDescription("(Optional) Minimum Support. Default Value: 2")
        .withShortName("s").create();
    
    Option analyzerNameOpt = obuilder.withLongName("analyzerName")
        .withArgument(
          abuilder.withName("analyzerName").withMinimum(1).withMaximum(1)
              .create()).withDescription("The class name of the analyzer")
        .withShortName("a").create();
    
    Option chunkSizeOpt = obuilder.withLongName("chunkSize").withArgument(
      abuilder.withName("chunkSize").withMinimum(1).withMaximum(1).create())
        .withDescription("The chunkSize in MegaBytes. 100-10000 MB")
        .withShortName("chunk").create();
    
    Option weightOpt = obuilder.withLongName("weight").withRequired(false)
        .withArgument(
          abuilder.withName("weight").withMinimum(1).withMaximum(1).create())
        .withDescription("The kind of weight to use. Currently TF or TFIDF")
        .withShortName("w").create();
    
    Option minDFOpt = obuilder.withLongName("minDF").withRequired(false)
        .withArgument(
          abuilder.withName("minDF").withMinimum(1).withMaximum(1).create())
        .withDescription("The minimum document frequency.  Default is 1")
        .withShortName("md").create();
    
    Option maxDFPercentOpt = obuilder
        .withLongName("maxDFPercent")
        .withRequired(false)
        .withArgument(
          abuilder.withName("maxDFPercent").withMinimum(1).withMaximum(1)
              .create())
        .withDescription(
          "The max percentage of docs for the DF.  Can be used to remove really high frequency terms.  Expressed as an integer between 0 and 100. Default is 99.")
        .withShortName("x").create();
    Option powerOpt = obuilder
        .withLongName("norm")
        .withRequired(false)
        .withArgument(
          abuilder.withName("norm").withMinimum(1).withMaximum(1).create())
        .withDescription(
          "The norm to use, expressed as either a float or \"INF\" if you want to use the Infinite norm.  "
              + "Must be greater or equal to 0.  The default is not to normalize")
        .withShortName("n").create();
    
    Group group = gbuilder.withName("Options").withOption(minSupportOpt)
        .withOption(analyzerNameOpt).withOption(chunkSizeOpt).withOption(
          outputDirOpt).withOption(inputDirOpt).withOption(minDFOpt)
        .withOption(maxDFPercentOpt).withOption(weightOpt).withOption(powerOpt)
        .create();
    
    Parser parser = new Parser();
    parser.setGroup(group);
    CommandLine cmdLine = parser.parse(args);
    
    String inputDir = (String) cmdLine.getValue(inputDirOpt);
    String outputDir = (String) cmdLine.getValue(outputDirOpt);
    
    int chunkSize = 100;
    if (cmdLine.hasOption(chunkSizeOpt)) {
      chunkSize = Integer.parseInt((String) cmdLine.getValue(chunkSizeOpt));
    }
    int minSupport = 2;
    if (cmdLine.hasOption(minSupportOpt)) {
      String minSupportString = (String) cmdLine.getValue(minSupportOpt);
      minSupport = Integer.parseInt(minSupportString);
    }
    
    Class<? extends Analyzer> analyzerClass = StandardAnalyzer.class;
    if (cmdLine.hasOption(analyzerNameOpt)) {
      String className = cmdLine.getValue(analyzerNameOpt).toString();
      analyzerClass = (Class<? extends Analyzer>) Class.forName(className);
      // try instantiating it, b/c there isn't any point in setting it if
      // you can't instantiate it
      analyzerClass.newInstance();
    }
    
    boolean processIdf = false;
    
    if (cmdLine.hasOption(weightOpt)) {
      String wString = cmdLine.getValue(weightOpt).toString();
      if (wString.equalsIgnoreCase("tf")) {
        processIdf = false;
      } else if (wString.equalsIgnoreCase("tfidf")) {
        processIdf = true;
      } else {
        throw new OptionException(weightOpt);
      }
    } else {
      processIdf = true;
    }
    
    int minDf = 1;
    if (cmdLine.hasOption(minDFOpt)) {
      minDf = Integer.parseInt(cmdLine.getValue(minDFOpt).toString());
    }
    int maxDFPercent = 99;
    if (cmdLine.hasOption(maxDFPercentOpt)) {
      maxDFPercent = Integer.parseInt(cmdLine.getValue(maxDFPercentOpt)
          .toString());
    }
    
    float norm = PartialVectorMerger.NO_NORMALIZING;
    if (cmdLine.hasOption(powerOpt)) {
      String power = cmdLine.getValue(powerOpt).toString();
      if (power.equals("INF")) {
        norm = Float.POSITIVE_INFINITY;
      } else {
        norm = Float.parseFloat(power);
      }
    }
    HadoopUtil.overwriteOutput(outputDir);
    String tokenizedPath = outputDir + "/tokenized-documents";
    DocumentProcessor.tokenizeDocuments(inputDir, analyzerClass, tokenizedPath);
    
    DictionaryVectorizer.createTermFrequencyVectors(tokenizedPath,
      outputDir, minSupport, chunkSize);
    if (processIdf) {
      TFIDFConverter.processTfIdf(
        outputDir + DictionaryVectorizer.DOCUMENT_VECTOR_OUTPUT_FOLDER,
        outputDir + "/tfidf", chunkSize, minDf, maxDFPercent, norm);
    }
  }
}
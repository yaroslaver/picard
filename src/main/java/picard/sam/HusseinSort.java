/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package picard.sam;

import htsjdk.samtools.*;
import htsjdk.samtools.util.*;
import org.broadinstitute.barclay.help.DocumentedFeature;
import picard.cmdline.CommandLineProgram;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import picard.cmdline.StandardOptionDefinitions;
import picard.cmdline.programgroups.ReadDataManipulationProgramGroup;
import picard.sam.util.YossiBAMRecordSet;
import picard.sam.util.YossiBAMRecordSetCodec;
import picard.util.QueryNameBatchIterator;

import java.io.File;

@CommandLineProgramProperties(
        summary = "Sort by chr+pos, with unmapped reads sorted consistently by bases of mate1, and then first-ness",
        oneLineSummary = "Sort by chr+pos, with unmapped reads sorted consistently by bases of mate1, and then first-ness",
        programGroup = ReadDataManipulationProgramGroup.class)
@DocumentedFeature
public class HusseinSort extends CommandLineProgram {
    @Argument(doc = "Input BAM or SAM file to sort.", shortName = StandardOptionDefinitions.INPUT_SHORT_NAME)
    public File INPUT;

    @Argument(doc = "Sorted BAM or SAM output file.", shortName = StandardOptionDefinitions.OUTPUT_SHORT_NAME)
    public File OUTPUT;

    private final Log log = Log.getInstance(SortSam.class);
    private int maxRecordsInRam = 500000;

    protected int doWork() {
        IOUtil.assertFileIsReadable(INPUT);
        IOUtil.assertFileIsWritable(OUTPUT);

        final SamReaderFactory srfactory = SamReaderFactory.makeDefault().setUseAsyncIo(false);
        final SamReader reader = srfactory.referenceSequence(REFERENCE_SEQUENCE).open(INPUT);

        SAMFileHeader header = reader.getFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.unsorted);
        header.setAttribute("SO", "husseinSort");

        final SamReader husseinQueryReader = srfactory.referenceSequence(REFERENCE_SEQUENCE).open(INPUT);

        SortingCollection<SAMRecord> readsSorter = SortingCollection.newInstance(SAMRecord.class,
                new BAMRecordCodec(header), new HusseinSortComparator(husseinQueryReader), maxRecordsInRam);

        final SAMFileWriter writer = new SAMFileWriterFactory().makeSAMOrBAMWriter(header, true, OUTPUT);
        writer.setProgressLogger(
                new ProgressLogger(log, (int) 1e7, "Wrote", "records from a sorting collection"));

        final ProgressLogger progress = new ProgressLogger(log, (int) 1e7, "Read");

        for( SAMRecord read : reader ) {
            readsSorter.add(read);
        }

        readsSorter.doneAdding();

        for (final SAMRecord rec : readsSorter) {
            writer.addAlignment(rec);
            progress.record(rec);
        }

        readsSorter.cleanup();
        writer.close();
        CloserUtil.close(reader);

        return 0;
    }
}
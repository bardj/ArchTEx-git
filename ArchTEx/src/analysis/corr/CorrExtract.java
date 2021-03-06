package analysis.corr;

import java.util.Vector;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import data.param.CorrParameter;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.util.CloseableIterator;

public class CorrExtract implements Runnable {
	final Lock lock = new ReentrantLock();
	final Condition inUse  = lock.newCondition(); 
	
	CorrParameter Parameters;
	Vector<CorrNode> ALLNodes;	
	
	int INDEX;
	int SUBSETSIZE;
	
	static int progressCounter = 0;
	static int currentProgress = 0;
	
	SAMFileReader inputSam;
	
	@Override
	public void run() {
		for(int x = INDEX; x < INDEX + SUBSETSIZE; x++) {
			inputSam = new SAMFileReader(Parameters.getInput().get(Parameters.getExp()), Parameters.getIndex().get(Parameters.getExp()));
			ALLNodes.get(x).setData(extractRegion(ALLNodes.get(x), Parameters));
			inputSam.close();
			
			//After tag data is added, calculate out statistics for the forward and reverse tags in the node
			double[] Ftag = ALLNodes.get(x).getData().get(0);
			double[] Rtag = ALLNodes.get(x).getData().get(1);
			
			//System.out.println(Ftag.length + "\t" + Rtag.length);
			double count = 0, sx = 0, sxx = 0, sy = 0, syy = 0;
			double[] sxy = new double[Parameters.getCorrWindow()];
			for(int winIndex = 0; winIndex < Ftag.length - Parameters.getCorrWindow(); winIndex++) {
				count++;
				sx += Ftag[winIndex];
				sxx += (Ftag[winIndex] * Ftag[winIndex]);
				sy += Rtag[winIndex];
				syy += (Rtag[winIndex] * Rtag[winIndex]);
				for(int shift = 0; shift < Parameters.getCorrWindow(); shift++) {
					sxy[shift] += (Ftag[winIndex] * Rtag[winIndex + shift]);
				}
			}
			
			ALLNodes.get(x).setCount(count);
			ALLNodes.get(x).setSx(sx);
			ALLNodes.get(x).setSxx(sxx);
			ALLNodes.get(x).setSy(sy);
			ALLNodes.get(x).setSyy(syy);
			ALLNodes.get(x).setSxy(sxy);
			
			//Remove residual tag data after analysis to conserve memory
			ALLNodes.get(x).setData(null);
			
			progressCounter++;
			int current = ((int)(0.5 + ((double)progressCounter / (double)ALLNodes.size() * 100)));
			if(current > currentProgress) {
				currentProgress = current;
				System.out.print(current + "%" + "\t0 {");
				for(int bar = 1; bar <= 20; bar++) {
					if(current >= bar * 5) { System.out.print("="); }
					else { System.out.print(" "); }
				}
				System.out.print("} 100\r");
				System.out.flush();
			}
		}
	}	
	
	public CorrExtract() {
		
	}
	
	public CorrExtract(CorrParameter param, Vector<CorrNode> nodes, int current, int size) {
		Parameters = param;
		ALLNodes = nodes;
		INDEX = current;
		SUBSETSIZE = size;
	}

	public Vector<double[]> extractRegion(CorrNode current, CorrParameter param) {
		int WINDOW = current.getStop() - current.getStart();
		if(WINDOW < 1) {
			System.out.println(WINDOW + "\t" + current.getStop() + "\t" + current.getStart() + "\t" + param.getResolution());
		}
		double[] Ftags = new double[(WINDOW / param.getResolution()) + 1];
		double[] Rtags = new double[(WINDOW / param.getResolution()) + 1];
		String CHROM = current.getChrom();
		int START = current.getStart();
		int STOP = current.getStop();
		int RESOLUTION = param.getResolution();
		
		//Create an iterator for every tag overlapping this region.
		try {
			CloseableIterator<SAMRecord> iter = inputSam.query(CHROM, START - 1, STOP + 1, false);
			/* Iterate through the records that overlap this region. */
			while (iter.hasNext()) {
				/* Create the record object */
				SAMRecord sr = iter.next();
				/* Get the start of the record */
				int recordStart = sr.getAlignmentStart();
				int windowMin = START;
				int windowMax = STOP;
				
				if (sr.getReadNegativeStrandFlag()) {
					recordStart = recordStart + sr.getReadLength() - 1;
					recordStart += 10;
					for(int i = recordStart; i >= recordStart - 21; i--){
						if(i - windowMin >= 0 && i <= windowMax && (i - START) % RESOLUTION == 0) {
							Rtags[(i - windowMin) / RESOLUTION]++;
						}
					}
					/*if(recordStart - windowMin >= 0 && recordStart <= windowMax && (recordStart - START) % RESOLUTION == 0) { 
						Rtags[(recordStart - windowMin) / RESOLUTION]++;
					}*/
				}
				else {
					recordStart -= 10;
					for(int i = recordStart; i <= recordStart + 21; i++){
						if(i - windowMin >= 0 && i <= windowMax && (i - START) % RESOLUTION == 0) {
							Ftags[(i - windowMin) / RESOLUTION]++;
						}
					}
					/*if(recordStart - windowMin >= 0 && recordStart <= windowMax && (recordStart - START) % RESOLUTION == 0) {
						Ftags[(recordStart - windowMin) / RESOLUTION]++;
					}*/
				}
			}
			iter.close();
		} catch (ArrayIndexOutOfBoundsException ex) {
			System.out.println("Exception caught");
		}
		Vector<double[]> retVal = new Vector<double[]>();
		retVal.add(Ftags);
		retVal.add(Rtags);
		return retVal;
	}
	
	public static void resetProgress() {
		progressCounter = 0;
		currentProgress = 0;
	}
}
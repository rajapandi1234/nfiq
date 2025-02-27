package org.mosip.nist.nfiq1.mlp;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.mosip.nist.nfiq1.common.IMlp;
import org.mosip.nist.nfiq1.common.IMlp.IRunMlp;
import org.mosip.nist.nfiq1.mindtct.Free;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunMlp extends Mlp implements IRunMlp {
	private static final Logger logger = LoggerFactory.getLogger(RunMlp.class);
	private static RunMlp instance;

	private RunMlp() {
		super();
	}

	public static synchronized RunMlp getInstance() {
		if (instance == null) {
			instance = new RunMlp();
		}
		return instance;
	}

	public Acs getAcs() {
		return Acs.getInstance();
	}

	public Free getFree() {
		return Free.getInstance();
	}

	public MlpCla getMlpCla() {
		return MlpCla.getInstance();
	}

	/*************************************************************/
	/*
	 * runmlp: Runs the Multi-Layer Perceptron (MLP) on a feature vector. Input
	 * args: nInps, nHids, nOuts: Numbers of input, hidden, and output nodes of the
	 * MLP. acFuncHidsCode: Code character specifying the type of activation
	 * function to be used on the hidden nodes: must be LINEAR, SIGMOID, or SINUSOID
	 * (defined in parms.h). acFuncOutsCode: Code character specifying the type of
	 * activation function to be used on the output nodes. weights: The MLP weights.
	 * featvec: The feature vector that the MLP is to be run on; its first nInps
	 * elts will be used. Output args: outAcs: The output activations. This buffer
	 * must be provided by caller, allocated to (at least) nOuts floats. hypClass:
	 * The hypothetical class, as an integer in the range 0 through nOuts - 1.
	 * confidence: A floating-point value in the range 0. through 1. Defined to be
	 * outAcs[hypClass], i.e. the highest output-activation value.
	 */
	public void runMlp(final int nInps, final int nHids, final int nOuts, final int acFuncHidsCode,
			final int acFuncOutsCode, AtomicReferenceArray<Double> weights, double[] featureVectorArr,
			AtomicReferenceArray<Double> outAcs, AtomicInteger hypClass, AtomicReference<Double> confidence) {

		AtomicReference<Character> runmlp1t = new AtomicReference<>();
		runmlp1t.set('t');

		AtomicInteger runmlp1i1 = new AtomicInteger();
		runmlp1i1.set(1);

		AtomicReference<Double> runmlp1f1 = new AtomicReference<Double>();
		runmlp1f1.set(1.0d);

		AtomicReferenceArray<Double> w1, b1, w2, b2;
		AtomicReferenceArray<Double> hidacs = new AtomicReferenceArray<>(IMlp.MAX_NHIDS);
		double pvalue;
		double pevalue;
		double maxacp;
		double maxac, ac;

		if (nHids > IMlp.MAX_NHIDS) {
			logger.error("ERROR : runmlp nHids, {}, is > MAX_NHIDS, defined as {} in runmlp ", nHids, IMlp.MAX_NHIDS);
			System.exit(-1);
		}

		/* Where the weights and biases of the two layers begin in weights. */
		w1 = weights;
		b1 = new AtomicReferenceArray<>(w1.length() + nHids * nInps);
		w2 = new AtomicReferenceArray<>(b1.length() + nHids);
		b2 = new AtomicReferenceArray<>(w2.length() + nOuts * nHids);

		int index = 0;
		/* Start hidden activations out as first-layer biases. */
		for (index = 0; index < nHids; index++)
			hidacs.set(index, b1.get(index));

		AtomicReferenceArray<Double> featvec = new AtomicReferenceArray<>(featureVectorArr.length);
		for (index = 0; index < featureVectorArr.length; index++) {
			featvec.set(index, featureVectorArr[index]);
		}

		/* Add product of first-layer weights with feature vector. */
		getMlpCla().mlpSgemV(runmlp1t, nInps, nHids, runmlp1f1, w1, nInps, featvec, runmlp1i1, runmlp1f1, hidacs,
				runmlp1i1);

		/* Finish each hidden activation by applying activation function. */
		index = 0;
		for (pevalue = (pvalue = hidacs.get(index)) + nHids; pvalue < pevalue; index++) {
			/* Resolve the activation function codes to functions. */
			switch (acFuncHidsCode) {
			case IMlp.LINEAR:
				getAcs().acVLinear(hidacs, index);
				break;
			case IMlp.SIGMOID:
				getAcs().acVSigmoid(hidacs, index);
				break;
			case IMlp.SINUSOID:
				getAcs().acVSinusoid(hidacs, index);
				break;
			default:
				logger.info(
						"runmlp :: unsupported acFuncHidsCode {}. nSupported codes are LINEAR ({}), SIGMOID ({}), and SINUSOID ({}).",
						(int) acFuncHidsCode, (int) IMlp.LINEAR, (int) IMlp.SIGMOID, (int) IMlp.SINUSOID);
				break;
			}
		}

		/* Same steps again for second layer. */
		for (index = 0; index < nOuts; index++)
			outAcs.set(index, b2.get(index));

		getMlpCla().mlpSgemV(runmlp1t, nHids, nOuts, runmlp1f1, w2, nHids, hidacs, runmlp1i1, runmlp1f1, outAcs,
				runmlp1i1);
		index = 0;
		for (pevalue = (pvalue = outAcs.get(index)) + nOuts; pvalue < pevalue; index++) {
			switch (acFuncOutsCode) {
			case IMlp.LINEAR:
				getAcs().acVLinear(outAcs, index);
				break;
			case IMlp.SIGMOID:
				getAcs().acVSigmoid(outAcs, index);
				break;
			case IMlp.SINUSOID:
				getAcs().acVSinusoid(outAcs, index);
				break;
			default:
				logger.info(
						"runmlp:: unsupported acFuncOutsCode {}. Supported codes are LINEAR ({}), SIGMOID ({}), and SINUSOID ({}).",
						(int) acFuncOutsCode, (int) IMlp.LINEAR, (int) IMlp.SIGMOID, (int) IMlp.SINUSOID);
				break;
			}
		}

		/*
		 * Find the hypothetical class -- the class whose output node activated most
		 * strongly -- and the confidence -- that activation value.
		 */
		index = 0;
		for (pevalue = (maxacp = pvalue = outAcs.get(index))
				+ nOuts, maxac = pvalue, index++; pvalue < pevalue; index++) {
			if ((ac = pvalue) > maxac) {
				maxac = ac;
				maxacp = pvalue;
			}
		}

		hypClass.set((int) ((int) maxacp - outAcs.get(0)));
		confidence.set(maxac);
	}

	/*************************************************************/
	/*
	 * runmlp2: Runs the Multi-Layer Perceptron (MLP) on a feature vector. Input
	 * args: nInps, nHids, nOuts: Numbers of input, hidden, and output nodes of the
	 * MLP. acFuncHidsCode: Code character specifying the type of activation
	 * function to be used on the hidden nodes: must be LINEAR, SIGMOID, or SINUSOID
	 * (defined in parms.h). acFuncOutsCode: Code character specifying the type of
	 * activation function to be used on the output nodes. weights: The MLP weights.
	 * featvec: The feature vector that the MLP is to be run on; its first nInps
	 * elts will be used. Output args: outAcs: The output activations. This buffer
	 * must be provided by caller, allocated to (at least) nOuts floats. hypClass:
	 * The hypothetical class, as an integer in the range 0 through nOuts - 1.
	 * confidence: A floating-point value in the range 0. through 1. Defined to be
	 * outAcs[hypClass], i.e. the highest output-activation value.
	 */
	public int runMlp2(final int nInps, final int nHids, final int nOuts, final int acFuncHidsCode,
			final int acFuncOutsCode, AtomicReferenceArray<Double> weights, double[] featureVectorArr,
			AtomicReferenceArray<Double> outAcs, AtomicInteger hypClass, AtomicReference<Double> confidence) {
		AtomicReference<Character> runMlp2T = new AtomicReference<>();
		runMlp2T.set('t');

		AtomicInteger runMlp2I1 = new AtomicInteger();
		runMlp2I1.set(1);

		AtomicReference<Double> runMlp2F1 = new AtomicReference<Double>();
		runMlp2F1.set(1.0d);

		double[] hidacsArr = new double[IMlp.MAX_NHIDS];
		double maxac = 0.0d;
		double ac;

		if (nHids > IMlp.MAX_NHIDS) {
			logger.error("ERROR : runmlp2 : nHids : {} > {}", nHids, IMlp.MAX_NHIDS);
			return (-2);
		}

		/* Where the weights and biases of the two layers begin in weights. */
		int wIndex = 0;
		int w1Index = wIndex;
		int b1Index = w1Index + nHids * nInps;
		int w2Index = b1Index + nHids;
		int b2Index = w2Index + nOuts * nHids;

		/* Start hidden activations out as first-layer biases. */
		int index = 0;
		for (index = 0; index < nHids; index++)
			hidacsArr[index] = weights.get(b1Index + index);

		AtomicReferenceArray<Double> hidacs = new AtomicReferenceArray<>(hidacsArr.length);
		for (index = 0; index < hidacsArr.length; index++) {
			hidacs.set(index, hidacsArr[index]);
		}

		AtomicReferenceArray<Double> featvec = new AtomicReferenceArray<>(featureVectorArr.length);
		for (index = 0; index < featureVectorArr.length; index++) {
			featvec.set(index, featureVectorArr[index]);
		}

		AtomicReferenceArray<Double> w1 = new AtomicReferenceArray<>(weights.length() - w1Index);
		for (index = 0; index < w1.length(); index++) {
			w1.set(index, weights.get(w1Index + index));
		}

		/* Add product of first-layer weights with feature vector. */
		getMlpCla().mlpSgemV(runMlp2T, nInps, nHids, runMlp2F1, w1, nInps, featvec, runMlp2I1, runMlp2F1, hidacs,
				runMlp2I1);

		for (index = 0; index < featvec.length(); index++) {
			featureVectorArr[index] = featvec.get(index);
		}

		int pIndex = 0;
		int peIndex = pIndex + nHids;
		/* Finish each hidden activation by applying activation function. */
		for (; pIndex < peIndex; pIndex++) {
			/* Resolve the activation function codes to functions. */
			switch (acFuncHidsCode) {
			case IMlp.LINEAR:
				getAcs().acVLinear(hidacs, pIndex);
				break;
			case IMlp.SIGMOID:
				getAcs().acVSigmoid(hidacs, pIndex);
				break;
			case IMlp.SINUSOID:
				getAcs().acVSinusoid(hidacs, pIndex);
				break;
			default:
				logger.error("ERROR : runmlp2 : acFuncHidsCode :{} unsupported\n", acFuncHidsCode);
				return (-3);
			}
		}

		/* Same steps again for second layer. */
		AtomicReferenceArray<Double> b2 = new AtomicReferenceArray<>(weights.length() - b2Index);
		for (index = 0; index < b2.length(); index++) {
			b2.set(index, weights.get(b2Index + index));
		}

		for (index = 0; index < nOuts; index++) {
			outAcs.set(index, b2.get(index));
		}

		AtomicReferenceArray<Double> w2 = new AtomicReferenceArray<>(weights.length() - w2Index);
		for (index = 0; index < w2.length(); index++) {
			w2.set(index, weights.get(w2Index + index));
		}

		getMlpCla().mlpSgemV(runMlp2T, nHids, nOuts, runMlp2F1, w2, nHids, hidacs, runMlp2I1, runMlp2F1, outAcs,
				runMlp2I1);

		pIndex = 0;
		peIndex = pIndex + nOuts;
		/* Finish each hidden activation by applying activation function. */
		for (; pIndex < peIndex; pIndex++) {
			switch (acFuncOutsCode) {
			case IMlp.LINEAR:
				getAcs().acVLinear(outAcs, pIndex);
				break;
			case IMlp.SIGMOID:
				getAcs().acVSigmoid(outAcs, pIndex);
				break;
			case IMlp.SINUSOID:
				getAcs().acVSinusoid(outAcs, pIndex);
				break;
			default:
				logger.error("ERROR : runmlp2 : acFuncOutsCode : {} unsupported\n", acFuncOutsCode);
				return (-4);
			}
		}

		/*
		 * Find the hypothetical class -- the class whose output node activated most
		 * strongly -- and the confidence -- that activation value.
		 */

		pIndex = 0;
		int maxacpIndex = pIndex;
		peIndex = maxacpIndex + nOuts;

		for (maxac = outAcs.get(pIndex), pIndex++; pIndex < peIndex; pIndex++) {
			if ((ac = outAcs.get(pIndex)) > maxac) {
				maxac = ac;
				maxacpIndex++;
			}
		}

		hypClass.set((maxacpIndex - 0));
		confidence.set(maxac);

		return 0;
	}
}
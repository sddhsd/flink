package eu.stratosphere.sopremo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import eu.stratosphere.dag.Printer;
import eu.stratosphere.dag.TraverseListener;
import eu.stratosphere.dag.Traverser;
import eu.stratosphere.pact.common.contract.Contract;
import eu.stratosphere.pact.common.contract.DataSinkContract;
import eu.stratosphere.pact.common.contract.DataSourceContract;
import eu.stratosphere.pact.common.contract.DualInputContract;
import eu.stratosphere.pact.common.contract.SingleInputContract;
import eu.stratosphere.pact.common.plan.PactModule;
import eu.stratosphere.pact.common.plan.Plan;
import eu.stratosphere.pact.common.type.base.PactJsonObject;
import eu.stratosphere.pact.common.type.base.PactNull;
import eu.stratosphere.sopremo.Operator.Output;
import eu.stratosphere.sopremo.operator.Sink;

public class SopremoPlan {
	private Collection<Operator> sinks;

	public SopremoPlan(Operator... sinks) {
		this.sinks = Arrays.asList(sinks);
	}

	public SopremoPlan(Collection<Operator> sinks) {
		this.sinks = sinks;
	}

	public static class PlanPrinter extends Printer<Operator> {
		public PlanPrinter(SopremoPlan plan) {
			super(new OperatorNavigator(), plan.getAllNodes());
		}
	}

	public Collection<Operator> getSinks() {
		return sinks;
	}

	@Override
	public String toString() {
		return new PlanPrinter(this).toString(80);
	}

	public List<Operator> getAllNodes() {
		final List<Operator> nodes = new ArrayList<Operator>();
		new Traverser<Operator>(new OperatorNavigator(), sinks).traverse(new TraverseListener<Operator>() {
			@Override
			public void nodeTraversed(Operator node) {
				nodes.add(node);
			}
		});
		return nodes;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Plan asPactPlan() {
		return new Plan((Collection) assemblePact());
	}

	Collection<Contract> assemblePact() {
		final Map<Operator, PactModule> modules = new IdentityHashMap<Operator, PactModule>();
		final Map<Operator, Contract[]> operatorOutputs = new IdentityHashMap<Operator, Contract[]>();

		new Traverser<Operator>(new OperatorNavigator(), sinks).traverse(new TraverseListener<Operator>() {
			@Override
			public void nodeTraversed(Operator node) {
				PactModule module = node.asPactModule();
				modules.put(node, module);
				DataSinkContract<PactNull, PactJsonObject>[] outputStubs = module.getOutputStubs();
				Contract[] outputContracts = new Contract[outputStubs.length];
				for (int index = 0; index < outputStubs.length; index++)
					outputContracts[index] = outputStubs[index].getInput();
				operatorOutputs.put(node, outputContracts);
			}
		});

		for (PactModule module : modules.values())
			module.validate();

		for (Entry<Operator, PactModule> operatorModule : modules.entrySet()) {
			Operator operator = operatorModule.getKey();
			PactModule module = operatorModule.getValue();
			List<DataSourceContract<PactNull, PactJsonObject>> moduleInputs = Arrays.asList(module.getInputStubs());

			Collection<Contract> contracts = module.getAllContracts();
			for (Contract contract : contracts) {
				Contract[] inputs = getInputs(contract);
				for (int index = 0; index < inputs.length; index++) {
					int inputIndex = moduleInputs.indexOf(inputs[index]);
					if (inputIndex != -1) {
						Output input = operator.getInputs().get(inputIndex);
						inputs[index] = operatorOutputs.get(input.getOperator())[input.getIndex()];
					}
				}
				setInputs(contract, inputs);
			}
		}

		List<Contract> pactSinks = new ArrayList<Contract>();
		for (Operator sink : sinks) {
			DataSinkContract<PactNull, PactJsonObject>[] outputs = modules.get(sink).getOutputStubs();
			for (DataSinkContract<PactNull, PactJsonObject> outputStub : outputs) {
				Contract output = outputStub;
				if (!(sink instanceof Sink))
					output = outputStub.getInput();
				pactSinks.add(output);
			}
		}

		return pactSinks;
	}

	private Contract[] getInputs(Contract contract) {
		if (contract instanceof SingleInputContract)
			return new Contract[] { ((SingleInputContract<?, ?, ?, ?>) contract).getInput() };
		if (contract instanceof DualInputContract)
			return new Contract[] { ((DualInputContract<?, ?, ?, ?, ?, ?>) contract).getFirstInput(),
				((DualInputContract<?, ?, ?, ?, ?, ?>) contract).getSecondInput() };
		return new Contract[0];
	}

	private void setInputs(Contract contract, Contract[] inputs) {
		if (contract instanceof SingleInputContract)
			((SingleInputContract<?, ?, ?, ?>) contract).setInput(inputs[0]);
		if (contract instanceof DualInputContract) {
			((DualInputContract<?, ?, ?, ?, ?, ?>) contract).setFirstInput(inputs[0]);
			((DualInputContract<?, ?, ?, ?, ?, ?>) contract).setSecondInput(inputs[1]);
		}
	}

	public static void main(String[] args) throws IOException {
		// Operator a = new Operator("A");
		// Operator c = new Operator("C", a, new Operator("B"));
		// Operator e = new Operator("E", c, new Operator("D"));
		// Operator f = new Operator("F", c, e, a);
		// Plan plan = new Plan(f);
		//
		// new PlanPrinter(plan).print(System.out, 10);
	}

	// public static class Source extends Node {
	// public Source() {
	// super();
	// }
	// }
	//
	// public static class Sink extends Node {
	// public Sink(Node... inputs) {
	// super(inputs);
	// }
	// }
	//
	// public static class
}

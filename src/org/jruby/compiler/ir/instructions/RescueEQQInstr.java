package org.jruby.compiler.ir.instructions;

import java.util.Map;
import org.jruby.compiler.ir.Operation;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.operands.UndefinedValue;
import org.jruby.compiler.ir.operands.Variable;
import org.jruby.compiler.ir.representations.InlinerInfo;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;

// This instruction is similar to EQQInstr, except it also verifies that
// the type to EQQ with is actually a class or a module since rescue clauses
// have this requirement unlike case statements.
//
// If v2 is an array, compare v1 with every element of v2 and stop on first match!
public class RescueEQQInstr extends Instr implements ResultInstr {
    private Operand arg1;
    private Operand arg2;
    private Variable result;

    public RescueEQQInstr(Variable result, Operand v1, Operand v2) {
        super(Operation.RESCUE_EQQ);
        
        assert result != null: "RescueEQQInstr result is null";
        
        this.arg1 = v1;
        this.arg2 = v2;
        this.result = result;
    }

    public Operand[] getOperands() {
        return new Operand[]{arg1, arg2};
    }

    public Variable getResult() {
        return result;
    }
    
    public void updateResult(Variable v) {
        this.result = v;
    }

    @Override
    public void simplifyOperands(Map<Operand, Operand> valueMap, boolean force) {
        arg1 = arg1.getSimplifiedOperand(valueMap, force);
        arg2 = arg2.getSimplifiedOperand(valueMap, force);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + arg1 + ", " + arg2 + ")";
    }

    public Instr cloneForInlining(InlinerInfo ii) {
        return new RescueEQQInstr(ii.getRenamedVariable(result), 
                arg1.cloneForInlining(ii), arg2.cloneForInlining(ii));
    }

    @Override
    public Object interpret(ThreadContext context, DynamicScope currDynScope, IRubyObject self, Object[] temp, Block block) {
        IRubyObject receiver = (IRubyObject) arg1.retrieve(context, self, currDynScope, temp);
        IRubyObject value = (IRubyObject) arg2.retrieve(context, self, currDynScope, temp);

        if (value == UndefinedValue.UNDEFINED) {
            return receiver;
        } else if (receiver instanceof RubyArray) {
            RubyArray testVals = (RubyArray)receiver;
            for (int i = 0, n = testVals.getLength(); i < n; i++) {
                IRubyObject excType = (IRubyObject)testVals.eltInternal(i);
                if (!(excType instanceof RubyModule)) {
                   throw context.getRuntime().newTypeError("class or module required for rescue clause. Found: " + excType);
                }
                IRubyObject eqqVal = excType.callMethod(context, "===", value);
                if (eqqVal.isTrue()) return eqqVal;
            }
            return context.getRuntime().newBoolean(false);
        } else {
            if (!(receiver instanceof RubyModule)) {
               throw context.getRuntime().newTypeError("class or module required for rescue clause. Found: " + receiver);
            }
            return receiver.callMethod(context, "===", value);
        }
    }
}

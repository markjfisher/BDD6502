Feature: Tests unitialised memory syntax

  Scenario: Test simple unitialised memory syntax
    Given I have a simple overclocked 6502 system
    And I create file "test.a" with
      """
      !sal
      *=$400
      start
        lda start
        ldx someData
        ldy someData+1  ; This is deliberately bad code because the memory has not been initialised
      willFailHere
        rts
      someData
        !by 12
      """

    Given I push a $73 byte to the stack
    Given I push a $71 byte to the stack
    Then I expect to see $1ff equal $73
    Then I expect to see $1fe equal $71

    And I run the command line: ..\C64\acme.exe -o test.prg --labeldump test.lbl -f cbm test.a
    And I load prg "test.prg"
    And I load labels "test.lbl"
    When I enable trace with indent
    When I set register A to $ff
    When I set register X to $ff
    When I set register Y to $ff
    Then I assert the unitialised memory read flag is clear
    # Not really needed, since it is disabled by default
    Then I disable unitialised memory read protection
    When I execute the procedure at start for no more than 100 instructions
    Then I assert the unitialised memory read flag is set
    # The procedure exited and the PC is returned to 0
    Then I expect register PC equal 0

    When I enable unitialised memory read protection
    # This will fail the step as soon as it happens
#    When I enable unitialised memory read protection with immediate fail
    # Needed if there have been previous executions in this 6502 system
    And I reset the unitialised memory read flag
    Then I assert the unitialised memory read flag is clear
    # Inplied load will set memory as written
    And I load prg "test.prg"
    When I execute the procedure at start for no more than 100 instructions
    # Here the code is deliberately bad, we want to check for the unitialised memory flag being set
    Then I assert the unitialised memory read flag is set
    # "I enable unitialised memory read protection" then the execution will stop
    Then I expect register PC equal willFailHere

    # When we write to the unitialised memory, initialising it, then the same code is no longer bad even with the unitialised check active
    When I write memory at someData + 1 with $73
    And I reset the unitialised memory read flag
    When I execute the procedure at start for no more than 100 instructions
    Then I assert the unitialised memory read flag is clear
    Then I expect register PC equal 0

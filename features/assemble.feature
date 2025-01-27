Feature: Assemble 6502 code test

  This assembles simple code and checks the expected results after executing it

  Scenario: Simple code test
    Given I have a simple 6502 system
    And I create file "test.a" with
  """
  !sal
  *=$400
  start
    lda #0
    sta $400
    ldx #$20
  .l1
    inc $400
    dex
    bne .l1
    rts
  """
    And I run the command line: ..\C64\acme.exe -o test.prg --labeldump test.lbl -f cbm test.a
    And I load prg "test.prg"
    And I load labels "test.lbl"
    Then I expect to see $400 equal 0xa9
    Then I expect to see $400 contain $81
    Then I expect to see $400 exclude $40
    # The above code is actually 100 instructions long when executing
    When I execute the procedure at start until return
    Then I assert the uninitialised memory read flag is clear
    # Note how the label "start" is used below and correctly resolves to be $400 when checking memory
    Then I expect to see start equal 32
    And I expect to see $402 equal $8d
    And I expect to see $400 equal 32

  Scenario: Using expressions with labels
    When I write memory at start + 12 - 3 with 12 + 7
    Then I expect to see 1024 + 9 equal 19

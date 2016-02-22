
describe command('java -version') do
  its(:stdout) { should match /java version "1\.8.*/ }
end